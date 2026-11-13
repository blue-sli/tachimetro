package app.tachimetro

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

/** Risultato della ricerca del nome della via. */
sealed interface StreetResult {
    data class Found(val name: String) : StreetResult

    /** I servizi hanno risposto, ma qui non c'è una via con un nome. */
    data object NotFound : StreetResult

    /** Nessuna risposta (per esempio manca internet): si tiene l'ultima via nota. */
    data object Failed : StreetResult
}

/**
 * Trova il nome della via: prima con il Geocoder di Android (servizio di Google già
 * presente sul telefono), poi, se non basta, con OpenStreetMap (Nominatim).
 */
class StreetNameResolver(context: Context) {

    private val geocoder: Geocoder? =
        if (Geocoder.isPresent()) Geocoder(context.applicationContext, Locale.ITALIAN) else null

    @Volatile
    private var ultimaChiamataNominatimMs = 0L

    suspend fun resolve(lat: Double, lon: Double): StreetResult {
        val daGeocoder = fromGeocoder(lat, lon)
        if (daGeocoder is StreetResult.Found) return daGeocoder

        return when (val daNominatim = fromNominatim(lat, lon)) {
            is StreetResult.Found, StreetResult.NotFound -> daNominatim
            // Nominatim non ha risposto o è stato saltato: decide il Geocoder.
            else -> if (daGeocoder == StreetResult.NotFound) StreetResult.NotFound else StreetResult.Failed
        }
    }

    private suspend fun fromGeocoder(lat: Double, lon: Double): StreetResult {
        val gc = geocoder ?: return StreetResult.Failed
        val indirizzi: List<Address>? = try {
            withTimeout(8_000) {
                if (Build.VERSION.SDK_INT >= 33) {
                    suspendCancellableCoroutine { cont ->
                        gc.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses)
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        gc.getFromLocation(lat, lon, 1)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // di solito: niente internet
        }

        indirizzi ?: return StreetResult.Failed
        val nome = indirizzi.firstOrNull()?.thoroughfare?.takeIf { it.isNotBlank() }
        return if (nome != null) StreetResult.Found(nome) else StreetResult.NotFound
    }

    /**
     * Regole d'uso di Nominatim (operations.osmfoundation.org/policies/nominatim):
     * al massimo 1 richiesta al secondo (qui al massimo 1 ogni 10 secondi),
     * uno User-Agent che identifica l'app, e nessun uso massiccio.
     * Restituisce null se la chiamata è stata saltata per rispettare questi limiti.
     */
    private suspend fun fromNominatim(lat: Double, lon: Double): StreetResult? =
        withContext(Dispatchers.IO) {
            val ora = SystemClock.elapsedRealtime()
            if (ultimaChiamataNominatimMs != 0L && ora - ultimaChiamataNominatimMs < NOMINATIM_INTERVALLO_MS) {
                return@withContext null
            }
            ultimaChiamataNominatimMs = ora

            val url = URL(
                String.format(
                    Locale.US,
                    "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%.6f&lon=%.6f&zoom=17&addressdetails=1",
                    lat, lon,
                )
            )
            var conn: HttpURLConnection? = null
            try {
                conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6_000
                    readTimeout = 6_000
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept-Language", "it")
                }
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext StreetResult.Failed
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parseNominatim(body)
            } catch (e: Exception) {
                StreetResult.Failed
            } finally {
                conn?.disconnect()
            }
        }

    companion object {
        private const val NOMINATIM_INTERVALLO_MS = 10_000L
        private const val USER_AGENT = "TachimetroAndroid/1.0 (app personale; Android)"

        /** Campi dell'indirizzo di Nominatim che contengono il nome di una via, in ordine di preferenza. */
        private val CAMPI_VIA = listOf("road", "pedestrian", "footway", "cycleway", "path", "square")

        internal fun parseNominatim(body: String): StreetResult {
            val json = JSONObject(body)
            if (json.has("error")) return StreetResult.NotFound
            val address = json.optJSONObject("address") ?: return StreetResult.NotFound
            for (campo in CAMPI_VIA) {
                val valore = address.optString(campo, "")
                if (valore.isNotBlank()) return StreetResult.Found(valore)
            }
            return StreetResult.NotFound
        }
    }
}
