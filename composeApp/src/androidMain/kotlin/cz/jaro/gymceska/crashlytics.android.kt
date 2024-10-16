package cz.jaro.gymceska

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.crashlytics.crashlytics

actual fun recordException(throwable: Throwable) {
    Firebase.crashlytics.recordException(throwable)
}