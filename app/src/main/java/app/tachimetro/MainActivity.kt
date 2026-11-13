package app.tachimetro

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tachimetro.ui.Ambra
import app.tachimetro.ui.GpsSpentoScreen
import app.tachimetro.ui.Nero
import app.tachimetro.ui.PermessoScreen
import app.tachimetro.ui.StatoPermesso
import app.tachimetro.ui.TachimetroScreen

class MainActivity : ComponentActivity() {

    private val viewModel: TachimetroViewModel by viewModels()

    private var inPip by mutableStateOf(false)
    private var permesso by mutableStateOf(StatoPermesso(concesso = false, soloApprossimativa = false, bloccato = false))

    private val richiestaPermesso = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        getPreferences(MODE_PRIVATE).edit().putBoolean(CHIAVE_GIA_CHIESTO, true).apply()
        aggiornaPermesso()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        super.onCreate(savedInstanceState)

        // Schermo sempre acceso finché l'app è visibile (anche nella finestrella).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        inPip = isInPictureInPictureMode
        addOnPictureInPictureModeChangedListener { info -> inPip = info.isInPictureInPictureMode }

        aggiornaPermesso()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Nero, surface = Nero, primary = Ambra)) {
                val state by viewModel.state.collectAsStateWithLifecycle()
                when {
                    !permesso.concesso -> PermessoScreen(
                        stato = permesso,
                        onRichiedi = ::chiediPermesso,
                        onApriImpostazioni = ::apriImpostazioniApp,
                    )
                    state.gps == GpsStatus.SPENTO && !inPip -> GpsSpentoScreen(onAttiva = ::apriImpostazioniPosizione)
                    else -> TachimetroScreen(state, inPip)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        aggiornaPermesso()
    }

    override fun onStop() {
        // Quando l'app non è più visibile (né a schermo intero né nella finestrella) fermiamo il GPS.
        viewModel.ferma()
        super.onStop()
    }

    /** Android 8–11: quando premi Home, entriamo noi nel Picture-in-Picture. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && permesso.concesso && pipDisponibile() && !isInPictureInPictureMode) {
            try {
                enterPictureInPictureMode(parametriPip())
            } catch (e: IllegalStateException) {
                // PiP disattivato per questa app nelle impostazioni del telefono
            }
        }
    }

    private fun aggiornaPermesso() {
        val preciso = concesso(Manifest.permission.ACCESS_FINE_LOCATION)
        val approssimativo = concesso(Manifest.permission.ACCESS_COARSE_LOCATION)
        val giaChiesto = getPreferences(MODE_PRIVATE).getBoolean(CHIAVE_GIA_CHIESTO, false)
        val bloccato = !preciso && giaChiesto &&
            !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)

        permesso = StatoPermesso(concesso = preciso, soloApprossimativa = !preciso && approssimativo, bloccato = bloccato)

        if (preciso) viewModel.avvia()
        aggiornaPip()
    }

    private fun concesso(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun chiediPermesso() {
        richiestaPermesso.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    private fun apriImpostazioniApp() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        )
    }

    private fun apriImpostazioniPosizione() {
        // NO_USER_ACTION: aprire le impostazioni non deve far entrare l'app nel Picture-in-Picture.
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION))
    }

    private fun pipDisponibile() = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** Android 12 e successivi (come il Galaxy S23): il sistema entra da solo nel PiP quando premi Home. */
    private fun aggiornaPip() {
        if (!pipDisponibile()) return
        try {
            setPictureInPictureParams(parametriPip())
        } catch (e: IllegalStateException) {
            // ignorato: PiP non disponibile
        }
    }

    private fun parametriPip(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(4, 3))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(permesso.concesso)
            builder.setSeamlessResizeEnabled(false) // testo, non video: ridisegna in modo pulito
        }
        return builder.build()
    }

    private companion object {
        const val CHIAVE_GIA_CHIESTO = "permesso_gia_chiesto"
    }
}
