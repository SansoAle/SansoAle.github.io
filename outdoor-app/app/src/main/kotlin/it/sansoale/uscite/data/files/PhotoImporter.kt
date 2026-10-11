package it.sansoale.uscite.data.files

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import it.sansoale.uscite.data.db.PhotoEntity
import it.sansoale.uscite.model.PhotoPositionSource
import it.sansoale.uscite.track.PhotoLocator
import it.sansoale.uscite.track.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/** Copia le foto scelte nella memoria dell'app, legge l'EXIF e genera le miniature. */
@Singleton
class PhotoImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val files: AppFiles,
) {

    suspend fun import(uri: Uri): PhotoEntity = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("heic") || mime.contains("heif") -> "heic"
            else -> "jpg"
        }
        val name = files.newName(ext)
        val target = files.photo(name)
        val input = openOriginal(uri) ?: throw IOException("Impossibile leggere la foto")
        input.use { inp -> target.outputStream().use { inp.copyTo(it) } }

        val exif = runCatching { ExifInterface(target) }.getOrNull()
        val latLon = exif?.latLong?.takeIf { it[0] != 0.0 || it[1] != 0.0 }
        val takenAt = exif?.let { readDateTime(it) }
        val thumbName = runCatching { createThumbnail(target, exif) }.getOrNull()
        val (w, h) = imageSize(target, exif)

        PhotoEntity(
            tripId = 0,
            fileName = name,
            thumbName = thumbName,
            takenAt = takenAt,
            lat = latLon?.get(0),
            lon = latLon?.get(1),
            positionSource = if (latLon != null) PhotoPositionSource.EXIF else PhotoPositionSource.NONE,
            width = w,
            height = h,
        )
    }

    /**
     * Le coordinate EXIF vengono oscurate da Android se l'app non ha ACCESS_MEDIA_LOCATION:
     * con il permesso si chiede l'originale, altrimenti si usa l'URI così com'è.
     */
    private fun openOriginal(uri: Uri): InputStream? {
        val canAskOriginal = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (canAskOriginal) {
            runCatching { context.contentResolver.openInputStream(MediaStore.setRequireOriginal(uri)) }
                .getOrNull()?.let { return it }
        }
        return context.contentResolver.openInputStream(uri)
    }

    /** Assegna una posizione alle foto senza GPS confrontando l'orario di scatto con la traccia. */
    fun locateByTime(photos: List<PhotoEntity>, timedPoints: List<TrackPoint>): List<PhotoEntity> = photos.map { p ->
        if (p.positionSource == PhotoPositionSource.EXIF) return@map p
        val pos = p.takenAt?.let { PhotoLocator.locateSorted(it, timedPoints) }
        if (pos != null) {
            p.copy(lat = pos.lat, lon = pos.lon, positionSource = PhotoPositionSource.TRACK_TIME)
        } else {
            p.copy(lat = null, lon = null, positionSource = PhotoPositionSource.NONE)
        }
    }

    fun deleteFiles(photo: PhotoEntity) {
        files.deleteQuietly(files.photo(photo.fileName), photo.thumbName?.let { files.thumb(it) })
    }

    private fun readDateTime(exif: ExifInterface): Long? {
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            ?: return null
        val offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_OFFSET_TIME)
        return parseExifDateTime(raw, offset)
    }

    private fun createThumbnail(source: File, exif: ExifInterface?): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMB_SIZE) sample *= 2
        val decoded = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val scale = THUMB_SIZE.toFloat() / max(decoded.width, decoded.height)
        val matrix = Matrix()
        if (scale < 1f) matrix.postScale(scale, scale)
        val rotation = exif?.rotationDegrees ?: 0
        if (rotation != 0) matrix.postRotate(rotation.toFloat())
        if (exif?.isFlipped == true) matrix.postScale(-1f, 1f)
        val thumb = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val name = files.newName("jpg")
        files.thumb(name).outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        if (thumb !== decoded) thumb.recycle()
        decoded.recycle()
        return name
    }

    private fun imageSize(file: File, exif: ExifInterface?): Pair<Int?, Int?> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        if (o.outWidth <= 0) return null to null
        val rotated = exif?.rotationDegrees?.let { it == 90 || it == 270 } == true
        return if (rotated) o.outHeight to o.outWidth else o.outWidth to o.outHeight
    }

    companion object {
        const val THUMB_SIZE = 512
        private val EXIF_FORMAT = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

        /**
         * Interpreta data/ora EXIF ("2024:07:14 09:30:00"). Se manca il fuso orario (OffsetTimeOriginal)
         * si usa quello del telefono.
         */
        fun parseExifDateTime(raw: String, offset: String?, zone: ZoneId = ZoneId.systemDefault()): Long? {
            val local = runCatching { LocalDateTime.parse(raw.trim().take(19), EXIF_FORMAT) }.getOrNull() ?: return null
            val zoneOffset = offset?.trim()?.let { runCatching { ZoneOffset.of(it) }.getOrNull() }
            return if (zoneOffset != null) {
                OffsetDateTime.of(local, zoneOffset).toInstant().toEpochMilli()
            } else {
                local.atZone(zone).toInstant().toEpochMilli()
            }
        }
    }
}
