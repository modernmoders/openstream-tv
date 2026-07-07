package dev.openstream.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Throttle state for the daily diagnostics upload (owner ask 2026-07-06). */
interface DiagnosticsUploadPrefs {
    suspend fun lastUploadMs(): Long
    suspend fun saveLastUploadMs(now: Long)
}

private val Context.diagnosticsUploadStore by preferencesDataStore("diagnostics_upload_prefs")

@Singleton
class DataStoreDiagnosticsUploadPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) : DiagnosticsUploadPrefs {

    private val key = longPreferencesKey("last_upload_ms")

    override suspend fun lastUploadMs(): Long =
        context.diagnosticsUploadStore.data.first()[key] ?: 0L

    override suspend fun saveLastUploadMs(now: Long) {
        context.diagnosticsUploadStore.edit { it[key] = now }
    }
}
