package app.tachimetro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tachimetro.GpsStatus
import app.tachimetro.Limite
import app.tachimetro.StreetStatus
import app.tachimetro.TachimetroState
import kotlin.math.min

val Nero = Color(0xFF000000)
val Bianco = Color(0xFFFFFFFF)
val Ambra = Color(0xFFFFC107)
val GrigioChiaro = Color(0xFFE0E0E0)
val Grigio = Color(0xFF9E9E9E)
val Rosso = Color(0xFFFF3B30)
val RossoCartello = Color(0xFFD32F2F)
val GrigioScuro = Color(0xFF616161)

@Composable
fun TachimetroScreen(state: TachimetroState, inPip: Boolean) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Nero)
    ) {
        when {
            inPip -> LayoutPip(state)
            maxWidth > maxHeight -> LayoutOrizzontale(state)
            else -> LayoutVerticale(state)
        }
    }
}

@Composable
private fun LayoutVerticale(state: TachimetroState) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.weight(0.2f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TestoGrande(state.direzione ?: "–", Ambra, larghezzaEm = 1.6f, modifier = Modifier.weight(1f).fillMaxSize())
            Cartello(state.limite, Modifier.fillMaxHeight(0.9f))
        }
        TestoGrande(velocita(state), coloreVelocita(state), larghezzaEm = 1.85f, modifier = Modifier.weight(0.5f).fillMaxWidth())
        Text("km/h", color = Grigio, fontSize = 28.sp)
        MessaggioGps(state.gps, 18)
        Box(Modifier.weight(0.3f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            BloccoVia(state, dimensione = 32, righe = 3)
        }
    }
}

@Composable
private fun LayoutOrizzontale(state: TachimetroState) {
    Row(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(0.6f).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            TestoGrande(velocita(state), coloreVelocita(state), larghezzaEm = 1.85f, modifier = Modifier.weight(1f).fillMaxWidth())
            Text("km/h", color = Grigio, fontSize = 24.sp)
            MessaggioGps(state.gps, 16)
        }
        Column(
            Modifier.weight(0.4f).fillMaxSize().padding(start = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(Modifier.weight(0.5f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TestoGrande(state.direzione ?: "–", Ambra, larghezzaEm = 1.6f, modifier = Modifier.weight(1f).fillMaxSize())
                Cartello(state.limite, Modifier.fillMaxHeight(0.85f))
            }
            Box(Modifier.weight(0.5f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                BloccoVia(state, dimensione = 28, righe = 3)
            }
        }
    }
}

/** Finestrella sopra le altre app: solo velocità, direzione e una riga per la via. */
@Composable
private fun LayoutPip(state: TachimetroState) {
    Column(Modifier.fillMaxSize().padding(6.dp)) {
        Row(Modifier.weight(0.78f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TestoGrande(velocita(state), coloreVelocita(state), larghezzaEm = 1.85f, modifier = Modifier.weight(0.68f).fillMaxSize())
            Column(Modifier.weight(0.32f).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                TestoGrande(state.direzione ?: "–", Ambra, larghezzaEm = 1.6f, modifier = Modifier.weight(0.4f).fillMaxWidth())
                Box(Modifier.weight(0.6f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Cartello(state.limite, Modifier.fillMaxHeight(0.95f))
                }
            }
        }
        TestoGrande(
            testoVia(state, breve = true),
            GrigioChiaro,
            larghezzaEm = 0f, // altezza fissa: i nomi lunghi vengono tagliati con "…"
            modifier = Modifier.weight(0.22f).fillMaxWidth(),
            peso = FontWeight.Normal,
        )
    }
}

@Composable
private fun BloccoVia(state: TachimetroState, dimensione: Int, righe: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            testoVia(state, breve = false),
            color = GrigioChiaro,
            fontSize = dimensione.sp,
            lineHeight = (dimensione * 1.15f).sp,
            textAlign = TextAlign.Center,
            maxLines = righe,
            overflow = TextOverflow.Ellipsis,
        )
        if (state.statoVia == StreetStatus.SENZA_INTERNET && state.via != null) {
            Text("senza internet: ultima via nota", color = Grigio, fontSize = 16.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MessaggioGps(gps: GpsStatus, dimensione: Int) {
    val testo = when (gps) {
        GpsStatus.IN_ATTESA -> "In attesa del segnale GPS…"
        GpsStatus.DEBOLE -> "Segnale GPS debole"
        GpsStatus.SPENTO -> "Posizione del telefono spenta"
        GpsStatus.OK -> null
    }
    if (testo != null) Text(testo, color = Ambra, fontSize = dimensione.sp, textAlign = TextAlign.Center)
}

private fun coloreVelocita(state: TachimetroState): Color = if (state.oltreIlLimite) Rosso else Bianco

/**
 * Cartello rotondo del limite, sempre visibile.
 * Limite vero: bordo rosso e numero nero, come i cartelli stradali.
 * Limite stimato: bordo e numero grigi, per far capire che non viene dalla mappa.
 * Nessun limite (null): cartello grigio con "--", per non inventare un numero.
 */
@Composable
internal fun Cartello(limite: Limite?, modifier: Modifier = Modifier) {
    val certo = limite != null && !limite.stimato
    val coloreBordo = if (certo) RossoCartello else Grigio
    val coloreNumero = if (certo) Nero else GrigioScuro
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val bordo = size.minDimension * 0.12f
            drawCircle(coloreBordo)
            drawCircle(Bianco, radius = size.minDimension / 2f - bordo)
        }
        val testo = limite?.kmh?.toString() ?: "--"
        TestoGrande(
            testo,
            coloreNumero,
            larghezzaEm = if (testo.length >= 3) 1.9f else 1.3f,
            modifier = Modifier.fillMaxSize(0.62f),
        )
    }
}

private fun velocita(state: TachimetroState): String = state.kmh?.toString() ?: "--"

private fun testoVia(state: TachimetroState, breve: Boolean): String = when {
    state.via != null -> state.via
    state.statoVia == StreetStatus.SENZA_NOME -> "Strada senza nome"
    state.statoVia == StreetStatus.SENZA_INTERNET -> if (breve) "Via non disponibile" else "Via non disponibile (senza internet)"
    else -> "Ricerca della via…"
}

/**
 * Testo su una riga, grande quanto lo spazio disponibile.
 * [larghezzaEm] = quanto è largo il testo più lungo previsto, in "em" (1 em = altezza del carattere):
 * per esempio "888" in grassetto è circa 1,85 em. Con 0 conta solo l'altezza.
 */
@Composable
internal fun TestoGrande(
    testo: String,
    colore: Color,
    larghezzaEm: Float,
    modifier: Modifier = Modifier,
    peso: FontWeight = FontWeight.Bold,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val dimensionePx = with(density) {
            val perAltezza = maxHeight.toPx() / 1.2f
            if (larghezzaEm > 0f) min(maxWidth.toPx() / larghezzaEm, perAltezza) else perAltezza * 0.75f
        }
        val dimensione = with(density) { dimensionePx.coerceAtLeast(1f).toSp() }
        Text(
            text = testo,
            color = colore,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontSize = dimensione,
                lineHeight = dimensione,
                fontWeight = peso,
                // Cifre tutte della stessa larghezza: il numero non "balla" quando cambia
                fontFeatureSettings = "tnum",
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
        )
    }
}
