package cz.jaro.gymceska.ukoly

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class JednoduchyUkol(
    @Serializable(with = MyUuidSerializer::class) val id: Uuid,
    val text: String,
    val stav: StavUkolu,
)
