package app.tachimetro

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class GpsStatus { IN_ATTESA, OK, DEBOLE, SPENTO }

enum class StreetStatus { IN_RICERCA, OK, SENZA_NOME, SENZA_INTERNET }

/** Tutto quello che lo schermo deve mostrare. */
data class TachimetroState(
    val kmh: Int? = null, // null = nessun segnale GPS recente
    val direzione: String? = null,
    val via: String? = null,
    val statoVia: StreetStatus = StreetStatus.IN_RICERCA,
    val gps: GpsStatus = GpsStatus.IN_ATTESA,
    val limite: Limite? = null, // limite della strada: vero (dalla mappa) o stimato
) {
    /** Rosso appena la velocità del GPS supera il limite (vero o stimato), anche solo di 1 km/h. */
    val oltreIlLimite: Boolean get() = kmh != null && limite != null && kmh > limite.kmh
}

class TachimetroViewModel(app: Application) : AndroidViewModel(app) {

    private val fused = LocationServices.getFusedLocationProviderClient(app)
    private val locationManager = app.getSystemService(LocationManager::class.java)
    private val resolver = StreetNameResolver(app)
    private val policy = StreetLookupPolicy()

    // Limite di velocità: scarichiamo le strade intorno a noi (circa 300 m) e le riconosciamo sul telefono.
    private val overpass = OverpassClient()
    private val policyStrade = StreetLookupPolicy(
        intervalloMs = 10_000,
        attesaDopoErroreMs = 30_000,
        distanzaMinimaM = 200f, // riscarichiamo prima di uscire dalla zona già scaricata
    )
    private var stradeVicine: List<Road> = emptyList()
    private var zoneAbitate: List<Area> = emptyList()
    private var centroStrade: Location? = null
    private var ultimoScaricamentoMs: Long? = null
    private var ultimoScaricamentoFallito = false
    private var scaricamentoJob: Job? = null
    private var ultimoLimite: Limite? = null
    private var ultimaStradaTrovataMs = 0L

    private val _state = MutableStateFlow(TachimetroState())
    val state: StateFlow<TachimetroState> = _state.asStateFlow()

    private var attivo = false
    private var controlloJob: Job? = null
    private var ricercaViaJob: Job? = null

    private var ultimaPosizione: Location? = null
    private var ultimaPosizioneRicevutaMs = 0L
    private var ultimaDirezione: Float? = null

    private var ultimoTentativoViaMs: Long? = null
    private var ultimoTentativoFallito = false
    private var posizioneUltimaVia: Location? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::nuovaPosizione)
        }
    }

    /** Avvia il GPS. Va chiamato solo dopo aver ottenuto il permesso di posizione. */
    @SuppressLint("MissingPermission")
    fun avvia() {
        if (attivo) return
        val richiesta = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateIntervalMillis(500L)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            fused.requestLocationUpdates(richiesta, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            return // permesso non concesso
        }
        attivo = true
        controlloJob = viewModelScope.launch {
            while (isActive) {
                controllaSegnale()
                delay(1_000)
            }
        }
    }

    /** Ferma il GPS quando l'app non è più visibile (risparmia batteria). */
    fun ferma() {
        if (!attivo) return
        attivo = false
        fused.removeLocationUpdates(callback)
        controlloJob?.cancel()
    }

    override fun onCleared() {
        ferma()
    }

    private fun nuovaPosizione(loc: Location) {
        val precedente = ultimaPosizione

        val speedMs = when {
            loc.hasSpeed() -> loc.speed
            precedente != null -> {
                val secondi = (loc.elapsedRealtimeNanos - precedente.elapsedRealtimeNanos) / 1e9f
                if (secondi > 0f) precedente.distanceTo(loc) / secondi else 0f
            }
            else -> 0f
        }

        val bearing: Float? = when {
            loc.hasBearing() -> loc.bearing
            precedente != null && precedente.distanceTo(loc) >= 10f -> precedente.bearingTo(loc)
            else -> null
        }
        val erroreDirezione = if (loc.hasBearing() && loc.hasBearingAccuracy()) loc.bearingAccuracyDegrees else null
        ultimaDirezione = Motion.direzioneAggiornata(ultimaDirezione, speedMs, bearing, erroreDirezione)

        ultimaPosizione = loc
        ultimaPosizioneRicevutaMs = SystemClock.elapsedRealtime()

        aggiornaLimite(loc)

        _state.update {
            it.copy(
                kmh = Motion.kmh(speedMs),
                direzione = ultimaDirezione?.let(Compass::sigla),
                gps = GpsStatus.OK,
                limite = ultimoLimite,
            )
        }
        forseCercaVia(loc)
        forseScaricaStrade(loc)
    }

    private fun forseCercaVia(loc: Location) {
        if (ricercaViaJob?.isActive == true) return
        val ora = SystemClock.elapsedRealtime()
        val distanza = posizioneUltimaVia?.distanceTo(loc)
        if (!policy.daCercare(ora, ultimoTentativoViaMs, ultimoTentativoFallito, distanza)) return

        ultimoTentativoViaMs = ora
        ricercaViaJob = viewModelScope.launch {
            when (val r = resolver.resolve(loc.latitude, loc.longitude)) {
                is StreetResult.Found -> {
                    posizioneUltimaVia = loc
                    ultimoTentativoFallito = false
                    _state.update { it.copy(via = r.name, statoVia = StreetStatus.OK) }
                }
                StreetResult.NotFound -> {
                    posizioneUltimaVia = loc
                    ultimoTentativoFallito = false
                    _state.update { it.copy(via = null, statoVia = StreetStatus.SENZA_NOME) }
                }
                StreetResult.Failed -> {
                    // Teniamo l'ultima via nota e segnaliamo che manca internet.
                    ultimoTentativoFallito = true
                    _state.update { it.copy(statoVia = StreetStatus.SENZA_INTERNET) }
                }
            }
        }
    }

    private fun aggiornaLimite(loc: Location) {
        val ora = SystemClock.elapsedRealtime()
        val punto = GeoPoint(loc.latitude, loc.longitude)
        val strada = SpeedLimits.trova(stradeVicine, punto, ultimaDirezione)
        if (strada != null) {
            ultimoLimite = SpeedLimits.limiteOStima(strada, SpeedLimits.inCitta(punto, zoneAbitate))
            ultimaStradaTrovataMs = ora
        } else if (ora - ultimaStradaTrovataMs > 5_000) {
            // Nessuna strada riconosciuta da 5 secondi: meglio non mostrare un limite sbagliato.
            ultimoLimite = null
        }
    }

    private fun forseScaricaStrade(loc: Location) {
        if (scaricamentoJob?.isActive == true) return
        val ora = SystemClock.elapsedRealtime()
        val distanza = centroStrade?.distanceTo(loc)
        if (!policyStrade.daCercare(ora, ultimoScaricamentoMs, ultimoScaricamentoFallito, distanza)) return

        ultimoScaricamentoMs = ora
        scaricamentoJob = viewModelScope.launch {
            val dati = overpass.datiVicini(loc.latitude, loc.longitude)
            if (dati != null) {
                stradeVicine = dati.strade
                zoneAbitate = dati.zoneAbitate
                centroStrade = loc
                ultimoScaricamentoFallito = false
            } else {
                // Senza internet teniamo le strade già scaricate: funzionano finché restiamo in zona.
                ultimoScaricamentoFallito = true
            }
        }
    }

    /** Ogni secondo: il GPS è acceso? Arrivano ancora posizioni? */
    private fun controllaSegnale() {
        val acceso = locationManager != null && LocationManagerCompat.isLocationEnabled(locationManager)
        val eta = SystemClock.elapsedRealtime() - ultimaPosizioneRicevutaMs
        val gps = when {
            !acceso -> GpsStatus.SPENTO
            ultimaPosizione == null -> GpsStatus.IN_ATTESA
            eta > 5_000 -> GpsStatus.DEBOLE
            else -> GpsStatus.OK
        }
        _state.update { s -> s.copy(gps = gps, kmh = if (gps == GpsStatus.OK) s.kmh else null) }
    }
}
