package app.tachimetro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Scarica da OpenStreetMap (servizio Overpass) le strade intorno a un punto, con i loro limiti.
 * Regole d'uso (dev.overpass-api.de/overpass-doc/en/preface/commons.html): meno di 10.000
 * richieste al giorno. Qui una richiesta copre circa 300 m e si ripete al massimo ogni 10 s.
 */
class OverpassClient {

    /** Null se non c'è risposta (per esempio manca internet). */
    suspend fun stradeVicine(lat: Double, lon: Double, raggioM: Int = RAGGIO_M): List<Road>? =
        withContext(Dispatchers.IO) {
            val query = String.format(
                Locale.US,
                "[out:json][timeout:15];way(around:%d,%.6f,%.6f)[highway~\"^(%s)$\"];out tags geom;",
                raggioM, lat, lon, TIPI_STRADA,
            )
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(INDIRIZZO).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 8_000
                    readTimeout = 20_000
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
                conn.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                leggiRisposta(body)
            } catch (e: Exception) {
                null
            } finally {
                conn?.disconnect()
            }
        }

    companion object {
        const val RAGGIO_M = 300
        private const val INDIRIZZO = "https://overpass-api.de/api/interpreter"
        private const val USER_AGENT = "TachimetroAndroid/1.0 (app personale; Android)"

        /** Solo strade dove passano le auto. */
        private const val TIPI_STRADA =
            "motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|service|road|" +
                "motorway_link|trunk_link|primary_link|secondary_link|tertiary_link"

        internal fun leggiRisposta(body: String): List<Road> {
            val elementi = JSONObject(body).optJSONArray("elements") ?: return emptyList()
            val strade = ArrayList<Road>(elementi.length())
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
                strade.add(Road(el.optLong("id"), tags, punti))
            }
            return strade
        }
    }
}
