package app.tachimetro

import org.junit.Assert.assertEquals
import org.junit.Test

class OverpassParseTest {

    @Test
    fun leggeStradeEGeometria() {
        val body = """
            {"version":0.6,"elements":[
              {"type":"way","id":123,
               "bounds":{"minlat":45.46,"minlon":9.18,"maxlat":45.47,"maxlon":9.19},
               "geometry":[{"lat":45.4640,"lon":9.1890},{"lat":45.4645,"lon":9.1900}],
               "tags":{"highway":"primary","name":"Via Roma","maxspeed":"50"}},
              {"type":"way","id":124,"geometry":[{"lat":45.4640,"lon":9.1890}],"tags":{"highway":"service"}},
              {"type":"way","id":200,"tags":{"landuse":"residential"},
               "geometry":[{"lat":45.0,"lon":9.0},{"lat":45.0,"lon":9.01},{"lat":45.01,"lon":9.01},{"lat":45.0,"lon":9.0}]},
              {"type":"node","id":5,"lat":45.0,"lon":9.0}
            ]}
        """.trimIndent()
        val dati = OverpassClient.leggiRisposta(body)
        val strade = dati.strade
        assertEquals(1, dati.zoneAbitate.size)
        assertEquals(4, dati.zoneAbitate[0].points.size)
        assertEquals(1, strade.size) // la strada con un solo punto e il nodo vengono scartati
        assertEquals(123L, strade[0].id)
        assertEquals("50", strade[0].tags["maxspeed"])
        assertEquals(2, strade[0].points.size)
        assertEquals(45.4645, strade[0].points[1].lat, 1e-9)
    }

    @Test
    fun rispostaVuota() {
        val dati = OverpassClient.leggiRisposta("""{"elements":[]}""")
        assertEquals(0, dati.strade.size)
        assertEquals(0, dati.zoneAbitate.size)
    }
}
