package app.tachimetro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedLimitsTest {

    @Test
    fun leggeINumeri() {
        assertEquals(50, SpeedLimits.leggiValore("50"))
        assertEquals(130, SpeedLimits.leggiValore(" 130 "))
        assertEquals(70, SpeedLimits.leggiValore("70 km/h"))
        assertEquals(48, SpeedLimits.leggiValore("30 mph"))
        assertEquals(30, SpeedLimits.leggiValore("30;50"))
    }

    @Test
    fun leggeLeSigleItaliane() {
        assertEquals(50, SpeedLimits.leggiValore("IT:urban"))
        assertEquals(90, SpeedLimits.leggiValore("IT:rural"))
        assertEquals(110, SpeedLimits.leggiValore("IT:trunk"))
        assertEquals(130, SpeedLimits.leggiValore("IT:motorway"))
    }

    @Test
    fun ignoraValoriSenzaLimite() {
        assertNull(SpeedLimits.leggiValore(null))
        assertNull(SpeedLimits.leggiValore("none"))
        assertNull(SpeedLimits.leggiValore("signals"))
        assertNull(SpeedLimits.leggiValore("walk"))
        assertNull(SpeedLimits.leggiValore("999"))
    }

    @Test
    fun limitePerSensoDiMarcia() {
        val tags = mapOf("maxspeed:forward" to "70", "maxspeed:backward" to "50", "maxspeed" to "90")
        assertEquals(70, SpeedLimits.limite(tags, forward = true))
        assertEquals(50, SpeedLimits.limite(tags, forward = false))
        assertEquals(90, SpeedLimits.limite(mapOf("maxspeed" to "90"), forward = false))
        assertEquals(50, SpeedLimits.limite(mapOf("maxspeed:type" to "IT:urban"), forward = true))
        assertNull(SpeedLimits.limite(mapOf("highway" to "residential"), forward = true))
    }

    @Test
    fun distanzaDaUnSegmento() {
        // Segmento verso est lungo l'equatore; punto circa 11 m a nord del suo centro.
        val a = GeoPoint(0.0, 0.0)
        val b = GeoPoint(0.0, 0.001)
        val d = SpeedLimits.distanzaDalSegmento(GeoPoint(0.0001, 0.0005), a, b)
        assertEquals(11.1, d, 0.2)
        // Oltre la fine del segmento conta la distanza dall'estremo.
        val oltre = SpeedLimits.distanzaDalSegmento(GeoPoint(0.0, 0.002), a, b)
        assertEquals(111.2, oltre, 0.5)
    }

    @Test
    fun angoliDeiSegmenti() {
        val o = GeoPoint(45.0, 9.0)
        assertEquals(0.0, SpeedLimits.angolo(o, GeoPoint(45.001, 9.0)), 0.5)
        assertEquals(90.0, SpeedLimits.angolo(o, GeoPoint(45.0, 9.001)), 0.5)
        assertEquals(180.0, SpeedLimits.angolo(o, GeoPoint(44.999, 9.0)), 0.5)
        assertEquals(270.0, SpeedLimits.angolo(o, GeoPoint(45.0, 8.999)), 0.5)
    }

    // Due strade parallele, una a 8 m a nord (verso est) e una a 8 m a sud.
    private val nord = Road(1, mapOf("maxspeed" to "50"), listOf(GeoPoint(45.00007, 9.0), GeoPoint(45.00007, 9.01)))
    private val sud = Road(2, mapOf("maxspeed" to "90"), listOf(GeoPoint(44.99993, 9.0), GeoPoint(44.99993, 9.01)))

    @Test
    fun sceglieLaStradaPiuVicina() {
        val match = SpeedLimits.trova(listOf(nord, sud), GeoPoint(45.00005, 9.005), direzione = null)
        assertEquals(1L, match?.road?.id)
    }

    @Test
    fun preferisceLaStradaOrientataComeNoi() {
        // Una strada verso nord a 5 m e una verso est a 8 m: andando verso est vince quella verso est.
        val traversa = Road(3, mapOf("maxspeed" to "30"), listOf(GeoPoint(44.99, 9.005064), GeoPoint(45.01, 9.005064)))
        val match = SpeedLimits.trova(listOf(traversa, nord), GeoPoint(45.0, 9.005), direzione = 90f)
        assertEquals(1L, match?.road?.id)
    }

    @Test
    fun riconosceIlSensoDiMarcia() {
        val versoEst = SpeedLimits.trova(listOf(nord), GeoPoint(45.00007, 9.005), direzione = 90f)
        val versoOvest = SpeedLimits.trova(listOf(nord), GeoPoint(45.00007, 9.005), direzione = 270f)
        assertNotNull(versoEst)
        assertTrue(versoEst!!.forward)
        assertFalse(versoOvest!!.forward)
    }

    @Test
    fun troppoLontanoNessunaStrada() {
        assertNull(SpeedLimits.trova(listOf(nord), GeoPoint(45.001, 9.005), direzione = null))
        assertNull(SpeedLimits.trova(emptyList(), GeoPoint(45.0, 9.0), direzione = null))
    }
}
