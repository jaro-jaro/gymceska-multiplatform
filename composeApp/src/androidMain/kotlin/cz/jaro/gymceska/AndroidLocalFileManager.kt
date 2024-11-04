package cz.jaro.gymceska

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.net.toFile
import androidx.core.net.toUri
import io.github.vinceglb.filekit.core.PlatformFile
import kotlin.io.path.outputStream

class AndroidLocalFileManager(
    private val context: Context,
) : LocalFileManager {
    override suspend fun PlatformFile.getSaveData() =
        kotlin.io.path.createTempFile(context.filesDir.toPath()).also { tempFile ->
            context.contentResolver.openInputStream(uri)?.use {
                tempFile.outputStream().use { os ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        it.transferTo(os)
                    } else {
                        it.copyTo(os)
                    }
                }
            }
        }.toFile().toUri().toString()

    override fun loadSavedTimetable(savedData: String) =
        Uri.parse(savedData).toFile().inputStream().use {
            it.readBytes().decodeToString()
        }

    override fun cleanup(savedData: String) =
        Uri.parse(savedData).toFile().delete().let {}
}