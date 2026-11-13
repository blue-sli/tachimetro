package app.tachimetro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Strade e zone abitate intorno a un punto. */
data class DatiMappa(val strade: List<Road>, val zoneAbitate: List<Area>)

/**
 * Scarica da OpenStreetMap (servizio Overpass) le strade intorno a un punto, con i loro limiti,
 * e le zone abitate (per stimare il limite quando manca).
 * Regole d'uso (dev.overpass-api.de/overpass-doc/en/preface/commons.html): meno di 10.000
 * richieste al giorno. Qui una richiesta copre circa 300 m e si ripete al massimo ogni 10 s.
 */
class OverpassClient {

    /** Null se non c'è risposta (per esempio manca internet). */
    suspend fun datiVicini(lat: Double, lon: Double, raggioM: Int = RAGGIO_M): DatiMappa? =
        withContext(Dispatchers.IO) {
            val query = String.format(
                Locale.US,
                "[out:json][timeout:15];(" +
                    "way(around:%1\$d,%2\$.6f,%3\$.6f)[highway~\"^(%4\$s)$\"];" +
                    "way(around:%1\$d,%2\$.6f,%3\$.6f)[landuse~\"^(residential|commercial|retail)$\"];" +
                    ");out tags geom;",
                raggioM, lat, lon, TIPI_STRADA,
            )
            // Se un server è sovraccarico o non risponde, proviamo il successivo.
            for (indirizzo in SERVER) {
                chiedi(indirizzo, query)?.let { return@withContext it }
            }
            null
        }

    private fun chiedi(indirizzo: String, query: String): DatiMappa? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(indirizzo).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            conn.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
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
        /** Server Overpass pubblici e gratuiti: il primo è quello principale, gli altri sono di riserva. */
        private val SERVER = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
        )
        private const val USER_AGENT = "TachimetroAndroid/1.0 (app personale; Android)"

        /** Solo strade dove passano le auto. */
        private const val TIPI_STRADA =
            "motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|service|road|" +
                "motorway_link|trunk_link|primary_link|secondary_link|tertiary_link"

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
