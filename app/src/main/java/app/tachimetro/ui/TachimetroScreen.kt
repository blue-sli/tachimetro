package app.tachimetro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import app.tachimetro.StreetStatus
import app.tachimetro.TachimetroState
import kotlin.math.min

val Nero = Color(0xFF000000)
val Bianco = Color(0xFFFFFFFF)
val Ambra = Color(0xFFFFC107)
val GrigioChiaro = Color(0xFFE0E0E0)
val Grigio = Color(0xFF9E9E9E)

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
        TestoGrande(state.direzione ?: "–", Ambra, larghezzaEm = 1.6f, modifier = Modifier.weight(0.2f).fillMaxWidth())
        TestoGrande(velocita(state), Bianco, larghezzaEm = 1.85f, modifier = Modifier.weight(0.5f).fillMaxWidth())
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
            TestoGrande(velocita(state), Bianco, larghezzaEm = 1.85f, modifier = Modifier.weight(1f).fillMaxWidth())
            Text("km/h", color = Grigio, fontSize = 24.sp)
            MessaggioGps(state.gps, 16)
        }
        Column(
            Modifier.weight(0.4f).fillMaxSize().padding(start = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TestoGrande(state.direzione ?: "–", Ambra, larghezzaEm = 1.6f, modifier = Modifier.weight(0.5f).fillMaxWidth())
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
            TestoGrande(velocita(state), Bianco, larghezzaEm = 1.85f, modifier = Modifier.weight(0.68f).fillMaxSize())
            Column(Modifier.weight(0.32f).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                TestoGrande(state.direzione ?: "–", Ambra, larghezzaEm = 1.6f, modifier = Modifier.weight(0.6f).fillMaxWidth())
                TestoGrande("km/h", Grigio, larghezzaEm = 2.6f, modifier = Modifier.weight(0.4f).fillMaxWidth())
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
private fun TestoGrande(
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
