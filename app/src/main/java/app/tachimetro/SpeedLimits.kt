package app.tachimetro

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

data class GeoPoint(val lat: Double, val lon: Double)

/** Una strada di OpenStreetMap: le sue etichette (nome, limite…) e i punti che la disegnano, in ordine. */
data class Road(val id: Long, val tags: Map<String, String>, val points: List<GeoPoint>)

/** Una zona abitata o commerciale disegnata sulla mappa: serve a capire se siamo "in città". */
data class Area(val points: List<GeoPoint>)

/** Limite di velocità da mostrare. [stimato] = non c'è sulla mappa, lo deduciamo dal Codice della Strada. */
data class Limite(val kmh: Int, val stimato: Boolean)

/** La strada su cui pensiamo di essere. [forward] = viaggiamo nel verso in cui è disegnata la strada. */
data class RoadMatch(val road: Road, val distanceM: Double, val forward: Boolean)

object SpeedLimits {

    /** Oltre questa distanza dalla strada più vicina non mostriamo nessun limite. */
    const val DISTANZA_MASSIMA_M = 30.0

    /** Quanti metri "pesa" una strada messa di traverso rispetto alla nostra direzione (90°). */
    private const val PENALITA_DIREZIONE_M = 20.0

    private const val RAGGIO_TERRA_M = 6_371_000.0

    /** Sigle che OpenStreetMap usa in Italia al posto del numero. */
    private val LIMITI_ITALIANI = mapOf(
        "IT:urban" to 50,
        "IT:rural" to 90,
        "IT:trunk" to 110,
        "IT:motorway" to 130,
    )

    private val SOLO_NUMERO = Regex("""^(\d{1,3})(?:\s*km/h)?$""")
    private val MIGLIA = Regex("""^(\d{1,3})\s*mph$""")

    /** Trasforma un valore come "50", "30 mph" o "IT:urban" in km/h. Null se non è un limite chiaro. */
    fun leggiValore(grezzo: String?): Int? {
        val testo = grezzo?.split(';')?.firstOrNull()?.trim() ?: return null
        val kmh = SOLO_NUMERO.find(testo)?.groupValues?.get(1)?.toInt()
            ?: MIGLIA.find(testo)?.groupValues?.get(1)?.toInt()?.let { (it * 1.609344).roundToInt() }
            ?: LIMITI_ITALIANI[testo]
        return kmh?.takeIf { it in 5..150 }
    }

    /** Il limite di una strada, tenendo conto dei limiti diversi nei due sensi di marcia. */
    fun limite(tags: Map<String, String>, forward: Boolean): Int? {
        val perSenso = leggiValore(tags[if (forward) "maxspeed:forward" else "maxspeed:backward"])
        return perSenso
            ?: leggiValore(tags["maxspeed"])
            ?: leggiValore(tags["maxspeed:type"])
            ?: leggiValore(tags["source:maxspeed"])
    }

    /**
     * Limite stimato quando la mappa non lo indica (Codice della Strada, art. 142).
     * Per svincoli, parcheggi e strade private non stimiamo: lì i limiti variano troppo.
     */
    fun stima(tipoStrada: String?, inCitta: Boolean): Int? = when (tipoStrada) {
        "motorway" -> 130
        "trunk" -> if (inCitta) 50 else 110
        "primary", "secondary", "tertiary", "unclassified", "road" -> if (inCitta) 50 else 90
        "residential" -> 50
        else -> null
    }

    /** Il limite vero se c'è, altrimenti quello stimato. */
    fun limiteOStima(strada: RoadMatch, inCitta: Boolean): Limite? {
        limite(strada.road.tags, strada.forward)?.let { return Limite(it, stimato = false) }
        return stima(strada.road.tags["highway"], inCitta)?.let { Limite(it, stimato = true) }
    }

    /** Vero se il punto è dentro almeno una delle zone abitate. */
    fun inCitta(p: GeoPoint, zone: List<Area>): Boolean = zone.any { dentro(p, it.points) }

    /** Test classico "punto nel poligono": conta quante volte una semiretta verso est attraversa il bordo. */
    fun dentro(p: GeoPoint, poligono: List<GeoPoint>): Boolean {
        if (poligono.size < 3) return false
        var dentro = false
        var j = poligono.size - 1
        for (i in poligono.indices) {
            val a = poligono[i]
            val b = poligono[j]
            if ((a.lat > p.lat) != (b.lat > p.lat)) {
                val lonIncrocio = a.lon + (p.lat - a.lat) / (b.lat - a.lat) * (b.lon - a.lon)
                if (p.lon < lonIncrocio) dentro = !dentro
            }
            j = i
        }
        return dentro
    }

    /**
     * Trova la strada su cui siamo: la più vicina, preferendo quelle orientate come la nostra
     * direzione di marcia (così una strada parallela o un incrocio non ci confondono).
     */
    fun trova(strade: List<Road>, posizione: GeoPoint, direzione: Float?): RoadMatch? {
        var migliore: RoadMatch? = null
        var punteggioMigliore = Double.MAX_VALUE

        for (strada in strade) {
            for (i in 0 until strada.points.size - 1) {
                val a = strada.points[i]
                val b = strada.points[i + 1]
                val distanza = distanzaDalSegmento(posizione, a, b)
                if (distanza > DISTANZA_MASSIMA_M) continue

                val angoloSegmento = angolo(a, b)
                var punteggio = distanza
                var forward = true
                if (direzione != null) {
                    val differenza = differenzaAngoli(direzione.toDouble(), angoloSegmento) // 0..180
                    forward = differenza <= 90.0
                    val storto = if (differenza > 90.0) 180.0 - differenza else differenza // 0..90
                    punteggio += storto / 90.0 * PENALITA_DIREZIONE_M
                }
                if (punteggio < punteggioMigliore) {
                    punteggioMigliore = punteggio
                    migliore = RoadMatch(strada, distanza, forward)
                }
            }
        }
        return migliore
    }

    /** Distanza in metri tra un punto e il segmento a–b (calcolo "in piano": va benissimo per pochi metri). */
    fun distanzaDalSegmento(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        val (ax, ay) = inMetri(a, p)
        val (bx, by) = inMetri(b, p)
        val dx = bx - ax
        val dy = by - ay
        val lunghezza2 = dx * dx + dy * dy
        val t = if (lunghezza2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / lunghezza2).coerceIn(0.0, 1.0)
        return hypot(ax + t * dx, ay + t * dy)
    }

    /** Angolo del segmento a→b: 0° = nord, 90° = est. */
    fun angolo(a: GeoPoint, b: GeoPoint): Double {
        val (bx, by) = inMetri(b, a)
        val gradi = Math.toDegrees(atan2(bx, by))
        return (gradi + 360.0) % 360.0
    }

    private fun differenzaAngoli(x: Double, y: Double): Double {
        val d = abs(((x - y) % 360.0 + 360.0) % 360.0)
        return if (d > 180.0) 360.0 - d else d
    }

    /** Coordinate di [p] in metri (est, nord) rispetto all'origine [o]. */
    private fun inMetri(p: GeoPoint, o: GeoPoint): Pair<Double, Double> {
        val x = Math.toRadians(p.lon - o.lon) * cos(Math.toRadians(o.lat)) * RAGGIO_TERRA_M
        val y = Math.toRadians(p.lat - o.lat) * RAGGIO_TERRA_M
        return x to y
    }
}
