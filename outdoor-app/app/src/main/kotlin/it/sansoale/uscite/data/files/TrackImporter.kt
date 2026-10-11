package it.sansoale.uscite.data.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import it.sansoale.uscite.data.db.StatsColumns
import it.sansoale.uscite.data.db.TrackEntity
import it.sansoale.uscite.domain.statsConfig
import it.sansoale.uscite.model.TripType
import it.sansoale.uscite.track.BoundingBox
import it.sansoale.uscite.track.GpxDocument
import it.sansoale.uscite.track.GpxParseException
import it.sansoale.uscite.track.GpxParser
import it.sansoale.uscite.track.GpxTrack
import it.sansoale.uscite.track.GpxWaypoint
import it.sansoale.uscite.track.GpxWriter
import it.sansoale.uscite.track.LatLon
import it.sansoale.uscite.track.PolylineCodec
import it.sansoale.uscite.track.TrackPoint
import it.sansoale.uscite.track.TrackPointsCodec
import it.sansoale.uscite.track.TrackSimplifier
import it.sansoale.uscite.track.TrackStatsCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ImportedTrack(
    val track: TrackEntity,
    /** Nome suggerito per l'uscita (dal GPX o dal nome del file). */
    val suggestedTitle: String?,
)

/** Importazione, lettura ed esportazione delle tracce GPX. */
@Singleton
class TrackImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val files: AppFiles,
) {

    /** Copia il GPX nella memoria dell'app, lo analizza e prepara la traccia (non ancora salvata nel DB). */
    suspend fun import(uri: Uri, type: TripType): ImportedTrack = withContext(Dispatchers.IO) {
        val gpxName = files.newName("gpx")
        val gpxFile = files.track(gpxName)
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Impossibile leggere il file")
        input.use { inp -> gpxFile.outputStream().use { inp.copyTo(it) } }
        val pointsFile = files.track(gpxName.removeSuffix(".gpx") + ".pts")
        try {
            val doc = gpxFile.inputStream().use { GpxParser.parse(it) }
            val segments = doc.segments
            if (segments.isEmpty()) throw GpxParseException("Il file GPX non contiene punti traccia")
            pointsFile.outputStream().use { TrackPointsCodec.write(segments, it) }
            val fileTitle = displayName(uri)?.substringBeforeLast('.')
            val name = doc.tracks.firstNotNullOfOrNull { it.name } ?: doc.name ?: fileTitle ?: "Traccia"
            ImportedTrack(
                track = buildEntity(name, gpxFile.name, pointsFile.name, segments, type),
                suggestedTitle = doc.name ?: doc.tracks.firstNotNullOfOrNull { it.name } ?: fileTitle,
            )
        } catch (e: Exception) {
            files.deleteQuietly(gpxFile, pointsFile)
            throw e
        }
    }

    fun buildEntity(
        name: String,
        gpxFile: String,
        pointsFile: String,
        segments: List<List<TrackPoint>>,
        type: TripType,
    ): TrackEntity {
        val stats = TrackStatsCalculator.compute(segments, type.statsConfig())
        val all = segments.flatten()
        val box = requireNotNull(BoundingBox.of(all))
        val simplified = segments.flatMap { TrackSimplifier.simplifyToMax(it, 400 / segments.size + 2) }
        return TrackEntity(
            tripId = 0,
            name = name,
            gpxFile = gpxFile,
            pointsFile = pointsFile,
            simplified = PolylineCodec.encode(simplified.map { LatLon(it.lat, it.lon) }),
            pointCount = all.size,
            stats = StatsColumns.from(stats),
            startTime = stats.startTime,
            endTime = stats.endTime,
            minLat = box.minLat,
            maxLat = box.maxLat,
            minLon = box.minLon,
            maxLon = box.maxLon,
        )
    }

    /** Punti della traccia dalla cache binaria; se manca o è corrotta viene rigenerata dal GPX. */
    suspend fun loadSegments(track: TrackEntity): List<List<TrackPoint>> = withContext(Dispatchers.IO) {
        val cache = files.track(track.pointsFile)
        runCatching { cache.inputStream().use { TrackPointsCodec.read(it) } }.getOrElse {
            val doc = files.track(track.gpxFile).inputStream().use { GpxParser.parse(it) }
            runCatching { cache.outputStream().use { out -> TrackPointsCodec.write(doc.segments, out) } }
            doc.segments
        }
    }

    /** Ricalcola le statistiche della traccia con i parametri del tipo di uscita. */
    suspend fun recompute(track: TrackEntity, type: TripType): TrackEntity {
        val segments = loadSegments(track)
        return withContext(Dispatchers.Default) {
            buildEntity(track.name, track.gpxFile, track.pointsFile, segments, type).copy(
                id = track.id,
                tripId = track.tripId,
                sortOrder = track.sortOrder,
            )
        }
    }

    /**
     * Esporta le tracce dell'uscita in un unico GPX. Con una sola traccia viene copiato il file
     * originale (nessuna perdita di dati, estensioni comprese).
     */
    suspend fun export(
        tracks: List<TrackEntity>,
        title: String,
        description: String?,
        waypoints: List<GpxWaypoint>,
        out: OutputStream,
    ) = withContext(Dispatchers.IO) {
        if (tracks.size == 1 && waypoints.isEmpty()) {
            val original = files.track(tracks.single().gpxFile)
            if (original.exists()) {
                original.inputStream().use { it.copyTo(out) }
                return@withContext
            }
        }
        val gpxTracks = tracks.map { t -> GpxTrack(t.name, loadSegments(t)) }
        GpxWriter.write(
            GpxDocument(
                name = title,
                description = description,
                creator = "Diario Uscite",
                time = tracks.mapNotNull { it.startTime }.minOrNull(),
                tracks = gpxTracks,
                waypoints = waypoints,
            ),
            out,
        )
    }

    fun deleteFiles(track: TrackEntity) {
        files.deleteQuietly(files.track(track.gpxFile), files.track(track.pointsFile))
    }

    fun gpxFile(track: TrackEntity): File = files.track(track.gpxFile)

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
}
