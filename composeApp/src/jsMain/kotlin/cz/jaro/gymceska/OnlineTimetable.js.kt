package cz.jaro.gymceska

import com.fleeksoft.ksoup.Ksoup
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.fetch.RequestInit

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