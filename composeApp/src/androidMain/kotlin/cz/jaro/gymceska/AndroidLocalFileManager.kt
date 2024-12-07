package cz.jaro.gymceska

import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import androidx.core.net.toUri
import cz.jaro.gymceska.rozvrh.manual.LocalFileManager
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText

class AndroidLocalFileManager(
    private val context: Context,
) : LocalFileManager {
    override suspend fun String.getSaveData() =
        createTempFile(context.filesDir.toPath()).also { tempFile ->
            tempFile.writeText(this)
        }.toFile().toUri().toString()

    override fun loadSavedTimetable(savedData: String) =
        Uri.parse(savedData).toFile().inputStream().use {
            it.readBytes().decodeToString()
        }

    override fun cleanup(savedData: String) =
        Uri.parse(savedData).toFile().delete().let {}
}