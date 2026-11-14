package app.tachimetro

import android.annotation.SuppressLint
import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
    val rotta: Float? = null, // direzione di marcia in gradi (0 = nord, 90 = est), per la bussola
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
    private var raggioStrade = SpeedLimits.raggioScaricamento(null)
    private var ultimoScaricamentoMs: Long? = null
    private var ultimoScaricamentoFallito = false
    private var scaricamentoJob: Job? = null
    private var ultimoLimite: Limite? = null
    private var ultimaStradaTrovataMs = 0L

    private val _state = MutableStateFlow(TachimetroState())
    val state: StateFlow<TachimetroState> = _state.asStateFlow()

    private var attivo = false
    private val ritmo = RitmoGps()

    // Accelerometro: acceso solo da fermi, per accorgerci subito della ripartenza.
    private val sensori = app.getSystemService(SensorManager::class.java)
    private val sensoreLineare: Sensor? = sensori?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val sensoreGrezzo: Sensor? = sensori?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val rilevatore = RilevatoreRipartenza()
    private var accelerometroAcceso = false
    private val gravita = FloatArray(3)

    private val ascoltaAccelerometro = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val v = event.values
            val (x, y, z) = if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                Triple(v[0], v[1], v[2])
            } else {
                // Senza sensore "lineare" togliamo noi la gravità con un filtro lento.
                for (i in 0..2) gravita[i] = 0.9f * gravita[i] + 0.1f * v[i]
                Triple(v[0] - gravita[0], v[1] - gravita[1], v[2] - gravita[2])
            }
            if (rilevatore.campione(x, y, z, event.timestamp / 1_000_000L)) ripartito()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
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
        ritmo.azzera()
        if (!richiediPosizioni()) return // permesso non concesso
        attivo = true
        controlloJob = viewModelScope.launch {
            while (isActive) {
                controllaSegnale()
                delay(1_000)
            }
        }
    }

    /**
     * Chiede le posizioni al GPS: una al secondo quando ci muoviamo,
     * una ogni 10 secondi quando siamo fermi da più di 2 secondi (risparmia batteria).
     * Anche da fermi accettiamo posizioni fino a una al secondo se il telefono le ha già
     * (per esempio perché un navigatore sta usando il GPS): così ci accorgiamo prima della ripartenza.
     * Richiamarla con lo stesso callback sostituisce la richiesta precedente.
     */
    @SuppressLint("MissingPermission")
    private fun richiediPosizioni(): Boolean {
        val intervallo = if (ritmo.lento) 10_000L else 1_000L
        val richiesta = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervallo)
            .setMinUpdateIntervalMillis(if (ritmo.lento) 1_000L else 500L)
            .setWaitForAccurateLocation(false)
            .build()
        return try {
            fused.requestLocationUpdates(richiesta, callback, Looper.getMainLooper())
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /** Ferma il GPS quando l'app non è più visibile (risparmia batteria). */
    fun ferma() {
        if (!attivo) return
        attivo = false
        spegniAccelerometro()
        fused.removeLocationUpdates(callback)
        controlloJob?.cancel()
        // Non lasciamo sullo schermo una velocità vecchia: alla riapertura si riparte da "--".
        _state.update { it.copy(kmh = null, gps = GpsStatus.IN_ATTESA) }
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

        val kmh = Motion.kmh(speedMs)
        if (ritmo.aggiorna(kmh, SystemClock.elapsedRealtime()) && attivo) {
            richiediPosizioni()
            if (ritmo.lento) accendiAccelerometro() else spegniAccelerometro()
        }

        _state.update {
            it.copy(
                kmh = kmh,
                direzione = ultimaDirezione?.let(Compass::sigla),
                rotta = ultimaDirezione,
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

    /** L'accelerometro ha sentito una ripartenza: GPS di nuovo veloce, subito. */
    private fun ripartito() {
        if (!attivo || !ritmo.lento) return
        ritmo.risveglia(SystemClock.elapsedRealtime())
        spegniAccelerometro()
        richiediPosizioni()
    }

    private fun accendiAccelerometro() {
        if (accelerometroAcceso) return
        val sensore = sensoreLineare ?: sensoreGrezzo ?: return
        rilevatore.azzera()
        gravita.fill(0f)
        // 25 letture al secondo: bastano e consumano pochissimo.
        accelerometroAcceso = sensori?.registerListener(ascoltaAccelerometro, sensore, 40_000) == true
    }

    private fun spegniAccelerometro() {
        if (!accelerometroAcceso) return
        sensori?.unregisterListener(ascoltaAccelerometro)
        accelerometroAcceso = false
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
        // Riscarichiamo quando abbiamo percorso il 60% del raggio dell'ultima zona:
        // così, anche se il server è lento, arriviamo ai nuovi dati prima di uscire da quelli vecchi.
        if (!policyStrade.daCercare(ora, ultimoScaricamentoMs, ultimoScaricamentoFallito, distanza, raggioStrade * 0.6f)) return

        ultimoScaricamentoMs = ora
        val kmh = _state.value.kmh
        val raggio = SpeedLimits.raggioScaricamento(kmh)
        val qui = GeoPoint(loc.latitude, loc.longitude)
        val direzione = ultimaDirezione
        // In movimento la zona è spostata in avanti di metà raggio, nella direzione di marcia.
        val centro = if (direzione != null && (kmh ?: 0) >= 20) {
            SpeedLimits.puntoAvanti(qui, direzione, raggio * 0.5)
        } else {
            qui
        }
        scaricamentoJob = viewModelScope.launch {
            val dati = overpass.datiVicini(centro.lat, centro.lon, raggio)
            if (dati != null) {
                stradeVicine = dati.strade
                zoneAbitate = dati.zoneAbitate
                centroStrade = loc
                raggioStrade = raggio
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
            eta > (if (ritmo.lento) 15_000 else 5_000) -> GpsStatus.DEBOLE
            else -> GpsStatus.OK
        }
        _state.update { s -> s.copy(gps = gps, kmh = if (gps == GpsStatus.OK) s.kmh else null) }
    }
}
