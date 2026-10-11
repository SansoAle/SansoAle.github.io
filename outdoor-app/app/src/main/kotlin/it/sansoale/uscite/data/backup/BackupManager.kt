package it.sansoale.uscite.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import it.sansoale.uscite.BuildConfig
import it.sansoale.uscite.data.db.AppDatabase
import it.sansoale.uscite.data.files.AppFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class BackupResult(val trips: Int, val tracks: Int, val photos: Int, val missingFiles: Int = 0)

/**
 * Backup completo dell'archivio in un file ZIP:
 * ```
 * archivio.json        ← tutte le tabelle del database
 * tracks/…             ← GPX originali e cache dei punti
 * photos/…             ← foto originali
 * thumbs/…             ← miniature
 * ```
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val files: AppFiles,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    suspend fun export(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val data = db.backupDao().exportAll().copy(
            appVersion = BuildConfig.VERSION_NAME,
            createdAt = System.currentTimeMillis(),
        )
        val output = context.contentResolver.openOutputStream(uri, "w") ?: throw IOException("Impossibile scrivere il file")
        ZipOutputStream(output.buffered()).use { zip ->
            zip.setLevel(Deflater.BEST_SPEED)
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for ((dir, names) in data.referencedFiles()) {
                for (name in names.sorted()) {
                    val file = File(files.dir(dir), name)
                    if (!file.isFile) continue
                    zip.putNextEntry(ZipEntry("$dir/$name").apply { time = file.lastModified() })
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        BackupResult(data.trips.size, data.tracks.size, data.photos.size)
    }

    /**
     * Ripristina un backup sostituendo l'intero archivio. Il contenuto viene prima estratto e verificato
     * in una cartella temporanea: l'archivio attuale viene toccato solo se il backup è valido.
     */
    suspend fun import(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val staging = File(files.cacheDir, "ripristino-${System.currentTimeMillis()}")
        staging.mkdirs()
        try {
            var manifest: String? = null
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Impossibile leggere il file")
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    if (entry.name == MANIFEST) {
                        manifest = zip.readBytes().toString(Charsets.UTF_8)
                        continue
                    }
                    val target = safeTarget(staging, entry.name) ?: continue
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
            }
            val text = manifest ?: throw IOException("Il file non è un backup del Diario Uscite (manca $MANIFEST)")
            val data = runCatching { json.decodeFromString(BackupData.serializer(), text) }
                .getOrElse { throw IOException("Backup danneggiato: ${it.message}", it) }
            if (data.formatVersion > BackupData.FORMAT_VERSION) {
                throw IOException("Backup creato con una versione più recente dell'app: aggiornala prima di ripristinarlo")
            }
            val missing = data.referencedFiles().entries.sumOf { (dir, names) ->
                names.count { !File(File(staging, dir), it).isFile }
            }

            db.backupDao().replaceAll(data)

            // Il database è stato sostituito: ora si scambiano le cartelle dei file.
            for (dir in AppFiles.ALL_DIRS) {
                val current = files.dir(dir)
                val incoming = File(staging, dir).apply { mkdirs() }
                val old = File(current.parentFile, "$dir.old")
                old.deleteRecursively()
                if (!current.renameTo(old)) throw IOException("Impossibile sostituire la cartella $dir")
                if (!incoming.renameTo(current)) {
                    incoming.copyRecursively(current, overwrite = true)
                }
                old.deleteRecursively()
            }
            BackupResult(data.trips.size, data.tracks.size, data.photos.size, missing)
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Accetta solo voci del tipo `cartella/nome` con cartelle note, per evitare path traversal. */
    private fun safeTarget(root: File, entryName: String): File? {
        val parts = entryName.split('/')
        if (parts.size != 2) return null
        val (dir, name) = parts
        if (dir !in AppFiles.ALL_DIRS || name.isBlank() || name.startsWith(".") || name.contains('\\')) return null
        return File(File(root, dir), name)
    }

    companion object {
        const val MANIFEST = "archivio.json"
        const val MIME_TYPE = "application/zip"
    }
}
