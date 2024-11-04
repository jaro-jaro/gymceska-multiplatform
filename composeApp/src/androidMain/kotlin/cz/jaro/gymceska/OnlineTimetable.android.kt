package cz.jaro.gymceska

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.network.parseGetRequest
import io.ktor.client.request.header

actual suspend fun getTimetableDocument(
    link: String,
) = Ksoup.parseGetRequest(link, {
    header("Accept-Language", "cs")
})