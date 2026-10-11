package it.sansoale.uscite

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre
import org.maplibre.android.offline.OfflineManager

@HiltAndroidApp
class UsciteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        // Cache locale delle tile già viste: le mappe consultate restano disponibili anche offline.
        runCatching { OfflineManager.getInstance(this).setMaximumAmbientCacheSize(MAP_CACHE_BYTES, null) }
    }

    companion object {
        const val MAP_CACHE_BYTES = 300L * 1024 * 1024
    }
}
