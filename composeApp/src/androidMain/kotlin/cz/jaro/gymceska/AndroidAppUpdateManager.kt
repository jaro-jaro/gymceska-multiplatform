package cz.jaro.gymceska

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.coroutineScope
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.network.parseGetRequest
import cz.jaro.better_dialog.AlertDialogManager
import cz.jaro.better_dialog.showMaterial
import cz.jaro.gymceska.FirebaseClassListSource.Companion.fromJson
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseApp
import dev.gitlive.firebase.remoteconfig.get
import dev.gitlive.firebase.remoteconfig.remoteConfig
import io.github.z4kn4fein.semver.toVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.time.Duration.Companion.hours


fun interface UserIdProvider {
    fun getUserId(): String
}

class AndroidAppUpdateManager(
    userOnlineManager: UserOnlineManager,
    userIdProvider: UserIdProvider,
    firebase: FirebaseApp,
) : UserOnlineManager by userOnlineManager, AdminManager {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val remoteConfig = Firebase.remoteConfig(firebase)

    private val configActive = flow {
        remoteConfig.settings {
            minimumFetchInterval = 1.hours
        }
        if (!isOnline())
            emit(remoteConfig.activate())
        else
            emit(remoteConfig.fetchAndActivate())
    }

    private suspend fun getAppUpdateNeeded(): Boolean {
        val localVersion = BuildKonfig.versionName.toVersion(false)

        if (localVersion.isPreRelease) return false

        val latestVersion = latestAppVersion()?.trim()?.toVersion(false) ?: return true

        return localVersion < latestVersion
    }

    val isAppUpdateNeeded = ::getAppUpdateNeeded.asFlow()

    override val isAdmin = configActive.map {
        val allowedDevices = remoteConfig.get<String>("povolenaZarizeni")
            .fromJson<List<String>>()

        val ja = userIdProvider.getUserId()

        ja in allowedDevices
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val breakingVersion = configActive.map {
        remoteConfig.get<String>("rozbitAplikaci").toIntOrNull() ?: -1
    }.stateIn(scope, SharingStarted.Eagerly, -1)
}

private suspend fun latestAppVersion(): String? = withContext(Dispatchers.IO) {
    val document = try {
        Ksoup.parseGetRequest("https://raw.githubusercontent.com/jaro-jaro/gymceska-multiplatform/main/composeApp/version.txt")
    } catch (e: IOException) {
        e.printStackTrace()
        recordException(e)
        return@withContext null
    }

    return@withContext document.text()
}

class AppUpdater(activity: MainActivity) {

    private val packageManager = activity.packageManager
    private val filesDir = activity.filesDir
    private val coroutineScope = activity.lifecycle.coroutineScope

    private val getUri: File.() -> Uri = {
        FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", this)
    }
    private val startActivity: (Intent) -> Unit = {
        activity.startActivity(it)
    }
    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { update() }

    private val intentToLaunch = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
        data = "package:${activity.packageName}".toUri()
    }

    fun update() {
        if (!packageManager.canRequestPackageInstalls()) {
            launcher.launch(intentToLaunch)
            return
        }
        val loading = AlertDialogManager.Global.showMaterial(
            state = "Hledání nové verze…",
            confirmButton = {},
            content = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text(customState, Modifier.padding(start = 8.dp))
                }
            },
        )

        val apkDir = File(filesDir, "apk").apply {
            if (!exists()) mkdir()
        }

        coroutineScope.launch(Dispatchers.Main) {
            val latestVersion = latestAppVersion() ?: return@launch

            val apkUrl =
                "https://github.com/jaro-jaro/gymceska-multiplatform/releases/download/v$latestVersion/Gymceska-$latestVersion.apk"

            val file = File(apkDir, "$latestVersion.apk")
            file.createNewFile()

            val connection = URL(apkUrl).openConnection() as HttpsURLConnection
            loading.customState = "Stahování…"
            connection.use { input ->
                withContext(Dispatchers.IO) {
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                loading.customState = "Instalace…"

                Intent(Intent.ACTION_VIEW).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    setDataAndType(file.getUri(), "application/vnd.android.package-archive")
                }.let(startActivity)
                loading.hide()
            }
        }
    }
}

suspend fun HttpURLConnection.use(block: suspend (BufferedInputStream) -> Unit) =
    withContext(Dispatchers.IO) {
        try {
            connect()
            inputStream.use {
                withContext(Dispatchers.Main) {
                    block(it.let(::BufferedInputStream))
                }
            }
        } finally {
            disconnect()
        }
    }

class MyFileProvider : FileProvider()