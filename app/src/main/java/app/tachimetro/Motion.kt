package app.tachimetro

import kotlin.math.roundToInt

/** Converte l'angolo di marcia (0° = nord, in senso orario) in una delle 8 sigle italiane. */
object Compass {
    private val SIGLE = arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")

    fun sigla(bearingDegrees: Float): String {
        val normalized = ((bearingDegrees % 360f) + 360f) % 360f
        val index = ((normalized + 22.5f) / 45f).toInt() % SIGLE.size
        return SIGLE[index]
    }
}

/** Regole per velocità e direzione, separate da Android così si possono testare. */
object Motion {
    /** Sotto questa velocità (circa 2 km/h) consideriamo l'auto ferma: il GPS "trema" anche da fermi. */
    const val SOGLIA_FERMO_MS = 0.55f

    /** Sotto circa 5 km/h la direzione del GPS è inaffidabile: teniamo l'ultima nota. */
    const val VELOCITA_MINIMA_DIREZIONE_MS = 1.4f

    /** Ignoriamo una direzione se il GPS stesso dice che può sbagliare di più di 45°. */
    const val MAX_ERRORE_DIREZIONE_GRADI = 45f

    fun kmh(speedMs: Float): Int =
        if (speedMs.isNaN() || speedMs < SOGLIA_FERMO_MS) 0 else (speedMs * 3.6f).roundToInt()

    fun direzioneAggiornata(
        precedente: Float?,
        speedMs: Float,
        bearing: Float?,
        erroreGradi: Float?,
    ): Float? {
        val affidabile = bearing != null &&
            speedMs >= VELOCITA_MINIMA_DIREZIONE_MS &&
            (erroreGradi == null || erroreGradi <= MAX_ERRORE_DIREZIONE_GRADI)
        return if (affidabile) bearing else precedente
    }
}

/**
 * Decide quando chiedere il nome della via: al massimo ogni [intervalloMs],
 * e solo se ci siamo spostati di almeno [distanzaMinimaM] dall'ultima via trovata.
 * Dopo un errore (per esempio senza internet) riprova più di rado, anche da fermi.
 */
class StreetLookupPolicy(
    private val intervalloMs: Long = 5_000,
    private val attesaDopoErroreMs: Long = 15_000,
    private val distanzaMinimaM: Float = 25f,
) {
    fun daCercare(
        oraMs: Long,
        ultimoTentativoMs: Long?,
        ultimoFallito: Boolean,
        distanzaDallUltimaViaM: Float?,
    ): Boolean {
        if (ultimoTentativoMs != null) {
            val attesa = if (ultimoFallito) attesaDopoErroreMs else intervalloMs
            if (oraMs - ultimoTentativoMs < attesa) return false
        }
        if (ultimoFallito) return true
        return distanzaDallUltimaViaM == null || distanzaDallUltimaViaM >= distanzaMinimaM
    }
}

/**
 * Risparmio batteria: se l'auto resta ferma per [attesaPrimaDiRallentareMs], il GPS può rallentare.
 * Appena la velocità torna sopra zero si torna subito al ritmo normale.
 */
class RitmoGps(private val attesaPrimaDiRallentareMs: Long = 2_000) {
    var lento = false
        private set
    private var fermoDaMs: Long? = null

    /** Da chiamare a ogni posizione. Restituisce true se il ritmo è appena cambiato. */
    fun aggiorna(kmh: Int, oraMs: Long): Boolean {
        val prima = lento
        if (kmh > 0) {
            fermoDaMs = null
            lento = false
        } else {
            val da = fermoDaMs ?: oraMs.also { fermoDaMs = it }
            if (oraMs - da >= attesaPrimaDiRallentareMs) lento = true
        }
        return lento != prima
    }

    fun azzera() {
        lento = false
        fermoDaMs = null
    }
}
