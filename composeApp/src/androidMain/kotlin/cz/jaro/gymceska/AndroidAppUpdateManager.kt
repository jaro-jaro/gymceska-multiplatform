package cz.jaro.gymceska

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.network.parseGetRequest
import cz.jaro.gymceska.FirebaseClassListSource.Companion.fromJson
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseApp
import dev.gitlive.firebase.remoteconfig.get
import dev.gitlive.firebase.remoteconfig.remoteConfig
import io.github.z4kn4fein.semver.toVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.io.IOException
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

    private suspend fun isAppUpdateNeeded(): Boolean {
        val mistniVerze = BuildKonfig.versionName.toVersion(false)

        if (mistniVerze.isPreRelease) return false

        val document = try {
            Ksoup.parseGetRequest("https://raw.githubusercontent.com/jaro-jaro/gymceska-multiplatform/main/composeApp/version.txt")
        } catch (e: IOException) {
            recordException(e)
            return false
        }

        val nejnovejsiVerze = document.text().trim().toVersion(false)

        return mistniVerze < nejnovejsiVerze
    }

    val isAppUpdateNeeded = flow {
        emit(isAppUpdateNeeded())
    }

    override val isAdmin = configActive.map {
        val povolene = remoteConfig.get<String>("povolenaZarizeni").fromJson<List<String>>()

        val ja = userIdProvider.getUserId()

        ja in povolene
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val breakingVersion = configActive.map {
        remoteConfig.get<String>("rozbitAplikaci").toIntOrNull() ?: -1
    }.stateIn(scope, SharingStarted.Eagerly, -1)
}