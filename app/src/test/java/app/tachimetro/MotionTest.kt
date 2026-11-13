package app.tachimetro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {

    @Test
    fun sigleDelleOttoDirezioni() {
        val attese = mapOf(
            0f to "N", 45f to "NE", 90f to "E", 135f to "SE",
            180f to "S", 225f to "SO", 270f to "O", 315f to "NO",
        )
        attese.forEach { (gradi, sigla) -> assertEquals("$gradi°", sigla, Compass.sigla(gradi)) }
    }

    @Test
    fun confiniTraLeDirezioni() {
        assertEquals("N", Compass.sigla(22.4f))
        assertEquals("NE", Compass.sigla(22.5f))
        assertEquals("NO", Compass.sigla(337.4f))
        assertEquals("N", Compass.sigla(337.5f))
        assertEquals("N", Compass.sigla(359.9f))
        assertEquals("N", Compass.sigla(360f))
        assertEquals("O", Compass.sigla(-90f)) // bearingTo() può restituire valori negativi
    }

    @Test
    fun velocitaInKmh() {
        assertEquals(0, Motion.kmh(0f))
        assertEquals(0, Motion.kmh(0.3f)) // tremolio del GPS da fermi
        assertEquals(50, Motion.kmh(13.89f))
        assertEquals(130, Motion.kmh(36.11f))
        assertEquals(0, Motion.kmh(Float.NaN))
    }

    @Test
    fun daFermiSiTieneLUltimaDirezione() {
        assertEquals(90f, Motion.direzioneAggiornata(90f, 0.2f, 200f, 10f))
        assertNull(Motion.direzioneAggiornata(null, 0.2f, 200f, 10f))
    }

    @Test
    fun inMovimentoSiAggiornaLaDirezione() {
        assertEquals(200f, Motion.direzioneAggiornata(90f, 10f, 200f, 10f))
        assertEquals(200f, Motion.direzioneAggiornata(90f, 10f, 200f, null))
    }

    @Test
    fun direzioneImprecisaIgnorata() {
        assertEquals(90f, Motion.direzioneAggiornata(90f, 10f, 200f, 80f))
        assertEquals(90f, Motion.direzioneAggiornata(90f, 10f, null, null))
    }

    @Test
    fun ricercaViaPrimaVolta() {
        val p = StreetLookupPolicy()
        assertTrue(p.daCercare(1_000, null, false, null))
    }

    @Test
    fun ricercaViaRispettaIntervallo() {
        val p = StreetLookupPolicy()
        assertFalse(p.daCercare(4_000, 0, false, 500f))
        assertTrue(p.daCercare(5_000, 0, false, 500f))
    }

    @Test
    fun ricercaViaSoloSeCiSiESpostati() {
        val p = StreetLookupPolicy()
        assertFalse(p.daCercare(60_000, 0, false, 10f))
        assertTrue(p.daCercare(60_000, 0, false, 30f))
    }

    @Test
    fun dopoUnErroreSiRiprovaPiuTardi() {
        val p = StreetLookupPolicy()
        assertFalse(p.daCercare(10_000, 0, true, 0f))
        assertTrue(p.daCercare(15_000, 0, true, 0f))
    }

    @Test
    fun ilGpsRallentaSoloDopoDueSecondiDaFermi() {
        val r = RitmoGps()
        assertFalse(r.aggiorna(0, 0))
        assertFalse(r.aggiorna(0, 1_000))
        assertFalse(r.lento)
        assertTrue(r.aggiorna(0, 2_000)) // cambio di ritmo
        assertTrue(r.lento)
        assertFalse(r.aggiorna(0, 12_000)) // già lento: nessun cambio
    }

    @Test
    fun appenaSiRipartesTornaVeloce() {
        val r = RitmoGps()
        r.aggiorna(0, 0)
        r.aggiorna(0, 2_000)
        assertTrue(r.aggiorna(12, 12_000))
        assertFalse(r.lento)
        // Una sosta breve non basta per rallentare di nuovo.
        assertFalse(r.aggiorna(0, 13_000))
        assertFalse(r.aggiorna(0, 14_000))
        assertTrue(r.aggiorna(0, 15_000))
    }
}
