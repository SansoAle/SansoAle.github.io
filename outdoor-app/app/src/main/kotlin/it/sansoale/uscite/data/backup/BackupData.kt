package it.sansoale.uscite.data.backup

import it.sansoale.uscite.data.db.BikeDetailEntity
import it.sansoale.uscite.data.db.CompanionEntity
import it.sansoale.uscite.data.db.HutEntity
import it.sansoale.uscite.data.db.MountainDetailEntity
import it.sansoale.uscite.data.db.PhotoEntity
import it.sansoale.uscite.data.db.TagEntity
import it.sansoale.uscite.data.db.TrackEntity
import it.sansoale.uscite.data.db.TripCompanionCrossRef
import it.sansoale.uscite.data.db.TripEntity
import it.sansoale.uscite.data.db.TripTagCrossRef
import it.sansoale.uscite.data.db.VanCostEntity
import it.sansoale.uscite.data.db.VanDetailEntity
import it.sansoale.uscite.data.db.VanStageEntity
import kotlinx.serialization.Serializable

/**
 * Contenuto del file `archivio.json` dentro lo ZIP di backup. Il database viene esportato in JSON
 * (anziché copiando il file SQLite) così il backup resta leggibile e importabile anche da versioni
 * future dell'app con uno schema diverso.
 */
@Serializable
data class BackupData(
    val formatVersion: Int = FORMAT_VERSION,
    val appVersion: String = "",
    val createdAt: Long = 0,
    val trips: List<TripEntity> = emptyList(),
    val mountain: List<MountainDetailEntity> = emptyList(),
    val huts: List<HutEntity> = emptyList(),
    val van: List<VanDetailEntity> = emptyList(),
    val stages: List<VanStageEntity> = emptyList(),
    val costs: List<VanCostEntity> = emptyList(),
    val bike: List<BikeDetailEntity> = emptyList(),
    val tracks: List<TrackEntity> = emptyList(),
    val photos: List<PhotoEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val tripTags: List<TripTagCrossRef> = emptyList(),
    val companions: List<CompanionEntity> = emptyList(),
    val tripCompanions: List<TripCompanionCrossRef> = emptyList(),
) {
    /** Nomi di tutti i file referenziati dall'archivio, per cartella. */
    fun referencedFiles(): Map<String, Set<String>> = mapOf(
        "tracks" to tracks.flatMap { listOf(it.gpxFile, it.pointsFile) }.toSet(),
        "photos" to photos.map { it.fileName }.toSet(),
        "thumbs" to photos.mapNotNull { it.thumbName }.toSet(),
    )

    companion object {
        const val FORMAT_VERSION = 1
    }
}
