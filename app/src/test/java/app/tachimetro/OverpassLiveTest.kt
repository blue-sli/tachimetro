package app.tachimetro

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Prova "dal vivo" dei server Overpass, per capire perché i limiti non arrivano.
 * Gira solo se la variabile OVERPASS_LIVE=1 (la imposta il controllo su GitHub Actions):
 * nei test normali viene saltata, perché dipende da internet.
 * Usa un punto pubblico noto (autostrada A4 vicino a Milano), non posizioni dell'utente.
 */
class OverpassLiveTest {

    private val lat = 45.5196
    private val lon = 9.2551

    private fun prova(nome: String, indirizzo: String, corpo: String?) {
        val inizio = System.currentTimeMillis()
        var conn: HttpURLConnection? = null
        val risultato = try {
            conn = (URL(indirizzo).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 90_000
                setRequestProperty("User-Agent", "TachimetroAndroid/1.0 (app personale; Android)")
                if (corpo != null) {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
            }
            if (corpo != null) conn.outputStream.use { it.write(("data=" + URLEncoder.encode(corpo, "UTF-8")).toByteArray()) }
            val codice = conn.responseCode
            val testo = (if (codice == 200) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            "codice=$codice byte=${testo.length} inizio=${testo.take(160).replace('\n', ' ')}"
        } catch (e: Exception) {
            "errore=$e"
        } finally {
            conn?.disconnect()
        }
        println("  [$nome] ${System.currentTimeMillis() - inizio} ms -> $risultato")
        Thread.sleep(3_000) // gentili con i server
    }

    @Test
    fun provaVarianti() {
        assumeTrue(System.getenv("OVERPASS_LIVE") == "1")
        val tipi = "motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|service|road|" +
            "motorway_link|trunk_link|primary_link|secondary_link|tertiary_link"
        val varianti = listOf(
            "banale" to "[out:json];node(1);out;",
            "solo strade, filtro prima" to
                "[out:json][timeout:25];way[highway~\"^($tipi)$\"](around:300,$lat,$lon);out tags geom;",
            "solo strade, filtro dopo" to
                "[out:json][timeout:25];way(around:300,$lat,$lon)[highway~\"^($tipi)$\"];out tags geom;",
            "solo zone abitate" to
                "[out:json][timeout:25];way[landuse~\"^(residential|commercial|retail)$\"](around:300,$lat,$lon);out tags geom;",
            "richiesta attuale dell'app" to OverpassClient.costruisciQuery(lat, lon),
        )
        for (server in listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
        )) {
            println("=== $server")
            prova("stato", server.replace("/interpreter", "/status"), null)
            for ((nome, q) in varianti) prova(nome, server, q)
        }
    }
}
