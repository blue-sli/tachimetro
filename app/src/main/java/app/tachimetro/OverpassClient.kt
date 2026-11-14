package app.tachimetro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Com'è andata una richiesta a un server: utile per capire i problemi. */
data class Esito(val codice: Int?, val errore: String?, val byte: Int, val dati: DatiMappa?)

/** Strade e zone abitate intorno a un punto. */
data class DatiMappa(val strade: List<Road>, val zoneAbitate: List<Area>)

/**
 * Scarica da OpenStreetMap (servizio Overpass) le strade intorno a un punto, con i loro limiti,
 * e le zone abitate (per stimare il limite quando manca).
 * Regole d'uso (dev.overpass-api.de/overpass-doc/en/preface/commons.html): meno di 10.000
 * richieste al giorno. Qui una richiesta copre da 600 a 1500 m e si ripete solo dopo
 * essersi spostati di buona parte di quella distanza (al massimo ogni 10 s).
 * I server pubblici sono spesso molto carichi: possono metterci 30 secondi a rispondere,
 * quindi aspettiamo fino a 50 secondi.
 */
class OverpassClient {

    /** Null se non c'è risposta (per esempio manca internet). */
    suspend fun datiVicini(lat: Double, lon: Double, raggioM: Int): DatiMappa? =
        withContext(Dispatchers.IO) {
            val query = costruisciQuery(lat, lon, raggioM)
            // Se un server è al completo (risponde 429/502/504) o non risponde, proviamo il successivo.
            for (indirizzo in SERVER) {
                chiedi(indirizzo, query).dati?.let { return@withContext it }
            }
            null
        }

    companion object {
        const val RAGGIO_M = 1000
        /** Server Overpass pubblici e gratuiti: il primo è quello principale, gli altri sono di riserva. */
        internal val SERVER = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
        )
        private const val USER_AGENT = "TachimetroAndroid/1.0 (app personale; Android)"

        /** Solo strade dove passano le auto (senza parcheggi e passi carrai, che appesantiscono i dati). */
        private const val TIPI_STRADA =
            "motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|road|" +
                "motorway_link|trunk_link|primary_link|secondary_link|tertiary_link"

        internal fun costruisciQuery(lat: Double, lon: Double, raggioM: Int = RAGGIO_M): String = String.format(
            Locale.US,
            "[out:json][timeout:40];(" +
                "way(around:%1\$d,%2\$.6f,%3\$.6f)[highway~\"^(%4\$s)$\"];" +
                "way(around:%1\$d,%2\$.6f,%3\$.6f)[landuse~\"^(residential|commercial|retail)$\"];" +
                ");out tags geom;",
            raggioM, lat, lon, TIPI_STRADA,
        )

        internal fun chiedi(indirizzo: String, query: String): Esito {
            var conn: HttpURLConnection? = null
            return try {
                conn = (URL(indirizzo).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 10_000
                    readTimeout = 50_000
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
                conn.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
                val codice = conn.responseCode
                if (codice != HttpURLConnection.HTTP_OK) {
                    val errore = conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(300)
                    return Esito(codice, errore, 0, null)
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                Esito(codice, null, body.length, leggiRisposta(body))
            } catch (e: Exception) {
                Esito(null, e.toString(), 0, null)
            } finally {
                conn?.disconnect()
            }
        }

        internal fun leggiRisposta(body: String): DatiMappa {
            val elementi = JSONObject(body).optJSONArray("elements") ?: return DatiMappa(emptyList(), emptyList())
            val strade = ArrayList<Road>(elementi.length())
            val zone = ArrayList<Area>()
            for (i in 0 until elementi.length()) {
                val el = elementi.optJSONObject(i) ?: continue
                if (el.optString("type") != "way") continue
                val geometria = el.optJSONArray("geometry") ?: continue
                val punti = (0 until geometria.length()).mapNotNull { j ->
                    geometria.optJSONObject(j)?.let { GeoPoint(it.optDouble("lat"), it.optDouble("lon")) }
                }.filter { !it.lat.isNaN() && !it.lon.isNaN() }
                if (punti.size < 2) continue
                val tags = HashMap<String, String>()
                el.optJSONObject("tags")?.let { t -> t.keys().forEach { k -> tags[k] = t.optString(k) } }
                when {
                    tags.containsKey("highway") -> strade.add(Road(el.optLong("id"), tags, punti))
                    tags.containsKey("landuse") && punti.size >= 3 -> zone.add(Area(punti))
                }
            }
            return DatiMappa(strade, zone)
        }
    }
}
