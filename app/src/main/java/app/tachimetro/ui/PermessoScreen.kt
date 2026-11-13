package app.tachimetro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Come sta il permesso di posizione. */
data class StatoPermesso(
    val concesso: Boolean,
    val soloApprossimativa: Boolean,
    val bloccato: Boolean, // l'utente ha detto "no" più volte: si può solo andare nelle Impostazioni
)

@Composable
fun PermessoScreen(stato: StatoPermesso, onRichiedi: () -> Unit, onApriImpostazioni: () -> Unit) {
    val messaggio = when {
        stato.soloApprossimativa ->
            "Hai concesso solo la posizione approssimativa: non basta per misurare la velocità.\n\n" +
                "Serve la posizione precisa."
        else ->
            "Il tachimetro usa il GPS del telefono per misurare la velocità e la direzione di marcia, " +
                "e per trovare il nome della via su cui stai viaggiando.\n\n" +
                "La posizione non viene salvata. Viene inviata solo ai servizi che trovano il nome " +
                "della via (Google e OpenStreetMap)."
    }
    val consiglio = if (stato.bloccato) {
        "Il permesso è stato negato. Aprilo dalle Impostazioni:\n" +
            "Autorizzazioni → Posizione → «Consenti solo mentre l'app è in uso», " +
            "e attiva «Usa posizione precisa»."
    } else {
        "Quando Android te lo chiede, scegli «Precisa» e poi «Mentre usi l'app»."
    }

    Schermata(
        titolo = "Serve la tua posizione",
        testo = messaggio,
        consiglio = consiglio,
        pulsante = if (stato.bloccato) "Apri le impostazioni" else "Consenti la posizione",
        onClick = if (stato.bloccato) onApriImpostazioni else onRichiedi,
    )
}

@Composable
fun GpsSpentoScreen(onAttiva: () -> Unit) {
    Schermata(
        titolo = "La posizione è spenta",
        testo = "Per misurare la velocità bisogna attivare la posizione del telefono.",
        consiglio = "Puoi anche scorrere dall'alto dello schermo e toccare l'icona «Posizione».",
        pulsante = "Attiva la posizione",
        onClick = onAttiva,
    )
}

@Composable
private fun Schermata(titolo: String, testo: String, consiglio: String, pulsante: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Nero)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(titolo, color = Bianco, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Text(testo, color = GrigioChiaro, fontSize = 19.sp, lineHeight = 26.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(consiglio, color = Grigio, fontSize = 17.sp, lineHeight = 24.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = Ambra, contentColor = Nero),
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Text(pulsante, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}
