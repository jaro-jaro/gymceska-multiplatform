package cz.jaro.gymceska

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.jaro.gymceska.theme.GymceskaTheme
import io.github.vinceglb.filekit.core.FileKit
import org.koin.compose.getKoin
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        enableEdgeToEdge(SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT), SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))

        FileKit.init(this)

        val uri = intent?.action?.equals(Intent.ACTION_VIEW)?.let { intent?.data }?.run { toString().removePrefix("${scheme}://${host}/#") }

        val rozvrh = intent.getBooleanExtra("rozvrh", false) || intent.getStringExtra("rozvrh") == "true"
        val ukoly = intent.getBooleanExtra("ukoly", false) || intent.getStringExtra("ukoly") == "true"

        val appUpdater = AppUpdater(this@MainActivity)

        setContent {
            val settings by koinInject<SettingsFlow>().collectAsStateWithLifecycle()
            val updateManager = koinInject<AndroidAppUpdateManager>()

            val breakingVersion by updateManager.breakingVersion.collectAsStateWithLifecycle()
            val isAppUpdateNeeded by updateManager.isAppUpdateNeeded.collectAsStateWithLifecycle(false)

            GymceskaTheme(
                useDarkTheme = if (settings.darkModePodleSystemu) isSystemInDarkTheme() else settings.darkMode,
                useDynamicColor = settings.dynamicColors,
                theme = settings.tema,
            ) {
                MainContent(
                    deeplink = when {
                        rozvrh -> "rozvrh"
                        ukoly -> "ukoly"
                        uri != null -> uri
                        else -> ""
                    },
                    updateApp = appUpdater::update,
                    isAppUpdateNeeded = isAppUpdateNeeded,
                    forceUpdate = breakingVersion >= BuildKonfig.versionCode,
                    koin = getKoin(),
                )
            }
        }
    }
}
