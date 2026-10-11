package it.sansoale.uscite.data.files

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Cartelle della memoria interna dell'app in cui vengono copiati tracce e foto. */
@Singleton
class AppFiles @Inject constructor(@ApplicationContext private val context: Context) {

    val tracksDir: File get() = dir(TRACKS)
    val photosDir: File get() = dir(PHOTOS)
    val thumbsDir: File get() = dir(THUMBS)

    fun dir(name: String): File = File(context.filesDir, name).apply { mkdirs() }

    fun track(name: String) = File(tracksDir, name)
    fun photo(name: String) = File(photosDir, name)
    fun thumb(name: String) = File(thumbsDir, name)

    fun newName(extension: String): String = "${UUID.randomUUID()}.$extension"

    fun deleteQuietly(vararg files: File?) {
        files.forEach { runCatching { it?.delete() } }
    }

    val cacheDir: File get() = context.cacheDir

    companion object {
        const val TRACKS = "tracks"
        const val PHOTOS = "photos"
        const val THUMBS = "thumbs"
        val ALL_DIRS = listOf(TRACKS, PHOTOS, THUMBS)
    }
}
