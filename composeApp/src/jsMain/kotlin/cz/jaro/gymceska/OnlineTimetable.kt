package cz.jaro.gymceska

import com.fleeksoft.ksoup.Ksoup
import kotlinx.browser.window
import kotlinx.coroutines.await

actual suspend fun getTimetableDocument(
    link: String,
) = Ksoup.parse(
    html = window.fetch(
        link,
        init = RequestInit(
            headers = JSON.parse("""{"Accept-Language":"cs"}"""),
        ),
    ).await().text().await(),
)