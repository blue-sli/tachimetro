package app.tachimetro

import org.junit.Assert.assertEquals
import org.junit.Test

class NominatimParseTest {

    @Test
    fun leggeIlNomeDellaVia() {
        val body = """{"address":{"road":"Via Roma","city":"Milano","country":"Italia"}}"""
        assertEquals(StreetResult.Found("Via Roma"), StreetNameResolver.parseNominatim(body))
    }

    @Test
    fun zonaPedonale() {
        val body = """{"address":{"pedestrian":"Piazza del Duomo","city":"Milano"}}"""
        assertEquals(StreetResult.Found("Piazza del Duomo"), StreetNameResolver.parseNominatim(body))
    }

    @Test
    fun nessunaVia() {
        assertEquals(StreetResult.NotFound, StreetNameResolver.parseNominatim("""{"address":{"city":"Milano"}}"""))
        assertEquals(StreetResult.NotFound, StreetNameResolver.parseNominatim("""{"error":"Unable to geocode"}"""))
    }
}
