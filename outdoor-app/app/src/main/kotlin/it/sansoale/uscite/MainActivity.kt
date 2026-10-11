package it.sansoale.uscite

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.IntentCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import it.sansoale.uscite.ui.navigation.AppNavHost
import it.sansoale.uscite.ui.settings.SettingsViewModel
import it.sansoale.uscite.ui.theme.UsciteTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** URI di un GPX ricevuto con "Condividi con" o "Apri con", in attesa di essere importato. */
    private val pendingImport = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            UsciteTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                AppNavHost(
                    settings = settings,
                    pendingImport = pendingImport.value,
                    onImportConsumed = { pendingImport.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        if (uri != null) pendingImport.value = uri.toString()
    }
}
