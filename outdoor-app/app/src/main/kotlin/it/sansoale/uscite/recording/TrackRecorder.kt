package it.sansoale.uscite.recording

import it.sansoale.uscite.track.TrackPoint
import kotlinx.coroutines.flow.StateFlow

/*
 * FASE 2 — Registrazione della traccia dal vivo (predisposizione, non ancora implementata).
 *
 * Progetto previsto:
 * - `TrackRecordingService`: foreground service con foregroundServiceType="location" e notifica
 *   persistente (pausa/riprendi/stop), così la registrazione continua a schermo spento.
 * - Posizioni da FusedLocationProvider (o LocationManager per restare senza Google Play Services),
 *   intervallo 1-5 s; i punti vengono scritti su file man mano (TrackPointsCodec) per non perderli
 *   se il processo viene terminato.
 * - Allo stop: generazione del GPX (GpxWriter), creazione dell'uscita con source = RECORDED e
 *   calcolo delle statistiche con TrackStatsCalculator, come per una traccia importata.
 * - Permessi: ACCESS_FINE_LOCATION, FOREGROUND_SERVICE, FOREGROUND_SERVICE_LOCATION,
 *   POST_NOTIFICATIONS (Android 13+).
 *
 * FASE 2 — Mappe offline: MapLibre supporta il download di regioni (OfflineManager.createOfflineRegion)
 * a partire dallo stile in MapStyles; va aggiunta una schermata per scegliere area e livelli di zoom.
 * Già ora le tile visualizzate restano in una cache locale (vedi UsciteApp).
 */

enum class RecordingStatus { IDLE, RECORDING, PAUSED }

data class RecordingState(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val startedAt: Long? = null,
    val distanceM: Double = 0.0,
    val lastPoint: TrackPoint? = null,
    val pointCount: Int = 0,
)

/** Contratto del futuro registratore di tracce. */
interface TrackRecorder {
    val state: StateFlow<RecordingState>
    fun start()
    fun pause()
    fun resume()

    /** Termina la registrazione e restituisce l'id dell'uscita creata. */
    suspend fun stop(): Long?
}
