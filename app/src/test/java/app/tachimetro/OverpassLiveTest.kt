package app.tachimetro

import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Prova "dal vivo" dei server Overpass, con la stessa richiesta dell'app.
 * Gira solo se la variabile OVERPASS_LIVE=1 (la imposta il controllo su GitHub Actions):
 * nei test normali viene saltata, perché dipende da internet.
 * Usa punti pubblici noti (autostrade e una via in città), non posizioni dell'utente.
 */
class OverpassLiveTest {

    private val punti = listOf(
        "A4 vicino a Milano" to GeoPoint(45.5196, 9.2551),
        "A1 vicino a Lodi" to GeoPoint(45.2922, 9.4180),
        "Centro di Milano" to GeoPoint(45.4642, 9.1900),
    )

    @Test
    fun provaIServer() {
        assumeTrue(System.getenv("OVERPASS_LIVE") == "1")
        for ((nome, p) in punti) {
            val query = OverpassClient.costruisciQuery(p.lat, p.lon)
            println("=== $nome (${p.lat}, ${p.lon})")
            for (server in OverpassClient.SERVER) {
                val inizio = System.currentTimeMillis()
                val esito = OverpassClient.chiedi(server, query)
                val ms = System.currentTimeMillis() - inizio
                val dati = esito.dati
                println(
                    "  $server -> codice=${esito.codice} byte=${esito.byte} ms=$ms " +
                        "strade=${dati?.strade?.size} zone=${dati?.zoneAbitate?.size} errore=${esito.errore}"
                )
                if (dati != null) {
                    val strada = SpeedLimits.trova(dati.strade, p, direzione = null)
                    val conLimite = dati.strade.count { it.tags.containsKey("maxspeed") }
                    println(
                        "    strade con maxspeed=$conLimite; più vicina=${strada?.road?.tags?.get("highway")} " +
                            "${strada?.road?.tags?.get("name") ?: strada?.road?.tags?.get("ref")} " +
                            "a ${strada?.distanceM?.toInt()} m; limite=" +
                            strada?.let { SpeedLimits.limiteOStima(it, SpeedLimits.inCitta(p, dati.zoneAbitate)) }
                    )
                }
                Thread.sleep(2_000) // gentili con i server
            }
        }
    }
}
