package cz.jaro.gymceska

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.coroutineScope
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.network.parseGetRequest
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import cz.jaro.gymceska.rozvrh.Vjec
import cz.jaro.gymceska.ui.theme.GymceskaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import java.net.SocketTimeoutException

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        enableEdgeToEdge(SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT), SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))

        val uri = intent?.action?.equals(Intent.ACTION_VIEW)?.let { intent?.data }?.run { toString().removePrefix("${scheme}://${host}/#") }

        val rozvrh = intent.getBooleanExtra("rozvrh", false) || intent.getStringExtra("rozvrh") == "true"
        val ukoly = intent.getBooleanExtra("ukoly", false) || intent.getStringExtra("ukoly") == "true"

        val aktualizovatAplikaci = {
            lifecycle.coroutineScope.launch(Dispatchers.IO) {
                val document = try {
                    withContext(Dispatchers.IO) {
                        Ksoup.parseGetRequest("https://raw.githubusercontent.com/jaro-jaro/gymceska-multiplatform/main/composeApp/version.txt")
                    }
                } catch (e: SocketTimeoutException) {
                    Firebase.crashlytics.recordException(e)
                    return@launch
                }

                val nejnovejsiVerze = document.text()

                startActivity(Intent().apply {
                    action = Intent.ACTION_VIEW
                    data =
                        Uri.parse("https://github.com/jaro-jaro/gymceska-multiplatform/releases/download/v$nejnovejsiVerze/Gymceska-v$nejnovejsiVerze.apk")
                })
            }
            Unit
        }

        setContent {
            val repo = koinInject<Repository>()

            val nastaveni by repo.nastaveni.collectAsStateWithLifecycle(Nastaveni(mojeTrida = Vjec.TridaVjec("")))
            val verzeNaRozbiti by repo.verzeNaRozbiti.collectAsStateWithLifecycle()
            val jePotrebaAktualizovatAplikaci by repo.jePotrebaAktualizovatAplikaci.collectAsStateWithLifecycle(false)

            GymceskaTheme(
                useDarkTheme = if (nastaveni.darkModePodleSystemu) isSystemInDarkTheme() else nastaveni.darkMode,
                useDynamicColor = nastaveni.dynamicColors,
                theme = nastaveni.tema,
            ) {
                if (verzeNaRozbiti >= BuildKonfig.versionCode) AlertDialog(
                    onDismissRequest = {},
                    confirmButton = {
                        TextButton(
                            onClick = {
                                startActivity(Intent().apply {
                                    action = Intent.ACTION_VIEW
                                    data = Uri.parse("https://github.com/jaro-jaro/gymceska-multiplatform/releases/latest")
                                })
                            }
                        ) {
                            Text("Přejít na GitHub")
                        }
                    },
                    properties = DialogProperties(
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false,
                    ),
                    title = {
                        Text("Tato aplikace je zastaralá")
                    },
                    text = {
                        Text("tak buď chytrý a nainstaluj si novou verzi")
                    },
                )

                MainContent(
                    deeplink = when {
                        rozvrh -> "rozvrh"
                        ukoly -> "ukoly"
                        uri != null -> uri
                        else -> ""
                    },
                    jePotrebaAktualizovatAplikaci = jePotrebaAktualizovatAplikaci,
                    aktualizovatAplikaci = aktualizovatAplikaci,
                    koin = getKoin(),
                )
            }
        }
    }
}
