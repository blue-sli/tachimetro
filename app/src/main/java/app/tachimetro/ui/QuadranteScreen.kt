package app.tachimetro.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.tachimetro.GpsStatus
import app.tachimetro.Limite
import app.tachimetro.StreetStatus
import app.tachimetro.TachimetroState
import kotlin.math.cos
import kotlin.math.sin

// Tema "Quadrante": quadrante rotondo con tacche, arco rosso luminoso e scritte tecniche spaziate.

private const val SCALA_MAX = 200f
private const val ANGOLO_INIZIO = 135f // in basso a sinistra (0° = ore 3, in senso orario)
private const val AMPIEZZA = 270f // fino in basso a destra

private val FondoCentro = Color(0xFF161616)
private val FondoBordo = Color(0xFF050505)
private val Bordo = Color(0xFF262626)
private val TaccaSpenta = Color(0xFF3A3A3A)
private val TaccaGrande = Color(0xFF8C8C8C)
private val Verde = Color(0xFF34C759)
private val Mono = FontFamily.Monospace

@Composable
fun QuadranteScreen(state: TachimetroState, inPip: Boolean) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Nero)
    ) {
        when {
            inPip -> QuadrantePip(state)
            maxWidth > maxHeight -> QuadranteOrizzontale(state)
            else -> QuadranteVerticale(state)
        }
    }
}

@Composable
private fun QuadranteVerticale(state: TachimetroState) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RigaStato(state, Modifier.fillMaxWidth())
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Quadrante(state, Modifier.fillMaxWidth())
        }
        BloccoViaQuadrante(state, Modifier.fillMaxWidth().padding(bottom = 24.dp))
    }
}

@Composable
private fun QuadranteOrizzontale(state: TachimetroState) {
    Row(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(0.58f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Quadrante(state, Modifier.fillMaxHeight())
        }
        Column(
            Modifier.weight(0.42f).fillMaxHeight().padding(start = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            RigaStato(state, Modifier.fillMaxWidth())
            BloccoViaQuadrante(state, Modifier.fillMaxWidth())
        }
    }
}

/**
 * Finestrella: il numero il più grande possibile, senza quadrante, e il cartello del limite.
 * Stesso carattere e stessi colori del tema: bianco, rosso oltre il limite, "KM/H" spaziato sotto.
 */
@Composable
private fun QuadrantePip(state: TachimetroState) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(FondoCentro, FondoBordo))),
        contentAlignment = Alignment.Center,
    ) {
        val altezzaUnita = with(LocalDensity.current) { (maxHeight * 0.13f).toSp() }
        Row(
            Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                TestoGrande(
                    state.kmh?.toString() ?: "--",
                    if (state.oltreIlLimite) Rosso else Bianco,
                    larghezzaEm = 1.85f,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                Text("KM/H", color = Grigio, fontFamily = Mono, fontSize = altezzaUnita, letterSpacing = 0.35.em)
            }
            // Cartello del limite a destra, sempre visibile ("--" se il limite non è noto).
            Spacer(Modifier.width(4.dp))
            Cartello(state.limite, Modifier.fillMaxHeight(0.45f))
        }
    }
}

/** In alto: pallino e stato del GPS a sinistra, direzione di marcia in una "pillola" rossa a destra. */
@Composable
private fun RigaStato(state: TachimetroState, modifier: Modifier = Modifier) {
    val (testo, colore) = when (state.gps) {
        GpsStatus.OK -> "GPS OK" to Verde
        GpsStatus.IN_ATTESA -> "RICERCA GPS" to Ambra
        GpsStatus.DEBOLE -> "GPS DEBOLE" to Ambra
        GpsStatus.SPENTO -> "GPS SPENTO" to Rosso
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .drawBehind { drawCircle(colore.copy(alpha = 0.35f), radius = size.minDimension) }
                .background(colore, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Text(testo, color = Grigio, fontFamily = Mono, fontSize = 14.sp, letterSpacing = 0.25.em)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .border(1.dp, Bordo, RoundedCornerShape(50))
                .padding(4.dp)
        ) {
            Box(
                Modifier
                    .drawBehind {
                        // alone rosso intorno alla pillola
                        drawRoundRect(
                            Rosso.copy(alpha = 0.25f),
                            topLeft = Offset(-6.dp.toPx(), -6.dp.toPx()),
                            size = Size(size.width + 12.dp.toPx(), size.height + 12.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height),
                        )
                    }
                    .background(Rosso, RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.direzione ?: "–",
                    color = Bianco,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 0.15.em,
                )
            }
        }
    }
}

/** In basso: cartello del limite e nome della via in maiuscolo, con una riga grigia di dettagli. */
@Composable
private fun BloccoViaQuadrante(state: TachimetroState, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Cartello(state.limite, Modifier.size(64.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f, fill = false), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                testoViaMaiuscolo(state),
                color = GrigioChiaro,
                fontFamily = Mono,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.12.em,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            dettagli(state)?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = Grigio, fontFamily = Mono, fontSize = 12.sp, letterSpacing = 0.12.em, textAlign = TextAlign.Center)
            }
        }
    }
}

private fun testoViaMaiuscolo(state: TachimetroState): String = when {
    state.via != null -> state.via
    state.statoVia == StreetStatus.SENZA_NOME -> "Strada senza nome"
    state.statoVia == StreetStatus.SENZA_INTERNET -> "Via non disponibile"
    else -> "Ricerca della via…"
}.uppercase()

private fun dettagli(state: TachimetroState): String? {
    val parti = buildList {
        state.limite?.let { add(if (it.stimato) "LIMITE ${it.kmh} · STIMATO" else "LIMITE ${it.kmh}") }
        if (state.statoVia == StreetStatus.SENZA_INTERNET && state.via != null) add("SENZA INTERNET")
    }
    return parti.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** Angolo (in gradi, per il Canvas) di una velocità sulla scala. */
private fun angoloDi(kmh: Float): Float = ANGOLO_INIZIO + AMPIEZZA * (kmh.coerceIn(0f, SCALA_MAX) / SCALA_MAX)

private fun DrawScope.puntoSulCerchio(angoloGradi: Float, raggio: Float): Offset {
    val rad = Math.toRadians(angoloGradi.toDouble())
    return Offset(center.x + (cos(rad) * raggio).toFloat(), center.y + (sin(rad) * raggio).toFloat())
}

/**
 * Il quadrante: tacche ogni 5 km/h (grandi ogni 25), arco rosso che segue la velocità,
 * tacca del limite e numero grande al centro. [scala] = mostra i numeri 0, 25, 50…
 */
@Composable
private fun Quadrante(state: TachimetroState, modifier: Modifier = Modifier, scala: Boolean = true) {
    val misuratore = rememberTextMeasurer()
    val haSegnale = state.kmh != null
    val velocita by animateFloatAsState(
        targetValue = (state.kmh ?: 0).toFloat(),
        animationSpec = tween(durationMillis = 500),
        label = "velocita",
    )
    val limite: Limite? = state.limite

    BoxWithConstraints(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        val diametroPx = with(LocalDensity.current) { maxWidth.toPx() }

        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f

            // Fondo del quadrante, leggermente più chiaro al centro, e bordo sottile.
            drawCircle(Brush.radialGradient(listOf(FondoCentro, FondoBordo), center = center, radius = r), radius = r)
            drawCircle(Bordo, radius = r - r * 0.005f, style = Stroke(width = r * 0.01f))

            // Tacche: rosse fino alla velocità attuale.
            for (v in 0..SCALA_MAX.toInt() step 5) {
                val grande = v % 25 == 0
                val angolo = angoloDi(v.toFloat())
                val accesa = haSegnale && v <= velocita
                val colore = when {
                    accesa -> Rosso
                    grande -> TaccaGrande
                    else -> TaccaSpenta
                }
                drawLine(
                    colore,
                    start = puntoSulCerchio(angolo, if (grande) r * 0.83f else r * 0.88f),
                    end = puntoSulCerchio(angolo, r * 0.94f),
                    strokeWidth = if (grande) r * 0.016f else r * 0.008f,
                    cap = StrokeCap.Round,
                )
            }

            // Tacca del limite: bianca se il limite è vero, grigia se è stimato.
            if (limite != null) {
                val angolo = angoloDi(limite.kmh.toFloat())
                drawLine(
                    if (limite.stimato) TaccaGrande else Bianco,
                    start = puntoSulCerchio(angolo, r * 0.79f),
                    end = puntoSulCerchio(angolo, r * 0.99f),
                    strokeWidth = r * 0.026f,
                    cap = StrokeCap.Round,
                )
            }

            // Arco rosso con alone luminoso.
            if (haSegnale && velocita > 0.5f) {
                val raggioArco = r * 0.76f
                val angoloArco = AMPIEZZA * (velocita.coerceAtMost(SCALA_MAX) / SCALA_MAX)
                val alto = Offset(center.x - raggioArco, center.y - raggioArco)
                val dimensione = Size(raggioArco * 2, raggioArco * 2)
                listOf(r * 0.10f to 0.06f, r * 0.055f to 0.12f, r * 0.03f to 0.25f).forEach { (spessore, alfa) ->
                    drawArc(
                        Rosso.copy(alpha = alfa), ANGOLO_INIZIO, angoloArco, useCenter = false,
                        topLeft = alto, size = dimensione, style = Stroke(spessore, cap = StrokeCap.Round),
                    )
                }
                drawArc(
                    Rosso, ANGOLO_INIZIO, angoloArco, useCenter = false,
                    topLeft = alto, size = dimensione, style = Stroke(r * 0.018f, cap = StrokeCap.Round),
                )
            }

            // Numeri della scala, all'interno dell'arco.
            if (scala) {
                for (v in 0..SCALA_MAX.toInt() step 25) {
                    val accesa = haSegnale && v <= velocita
                    val layout = misuratore.measure(
                        v.toString(),
                        TextStyle(
                            color = if (accesa) Rosso else TaccaGrande,
                            fontFamily = Mono,
                            fontSize = (r * 0.075f).toSp(),
                        ),
                    )
                    val p = puntoSulCerchio(angoloDi(v.toFloat()), r * 0.64f)
                    drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
                }
            }
        }

        // Numero grande e unità al centro.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TestoGrande(
                state.kmh?.toString() ?: "--",
                if (state.oltreIlLimite) Rosso else Bianco,
                larghezzaEm = 1.85f,
                modifier = Modifier.fillMaxWidth(0.5f).fillMaxHeight(0.3f),
            )
            Text(
                "KM/H",
                color = Grigio,
                fontFamily = Mono,
                fontSize = with(LocalDensity.current) { (diametroPx * 0.045f).toSp() },
                letterSpacing = 0.35.em,
            )
        }
    }
}
