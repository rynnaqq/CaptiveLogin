package com.example.hotspotportal.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import android.util.Log

/**
 * Event log for the Logs tab. Capped at 1000 rows in Room; the shared flow
 * lets the UI update immediately without a requery.
 */
class EventLog(private val dao: PortalLogDao, private val scope: kotlinx.coroutines.CoroutineScope) {

    private val _live = MutableSharedFlow<PortalLogEntity>(extraBufferCapacity = 64)
    val live = _live.asSharedFlow()

    val entries: Flow<List<PortalLogEntity>> = dao.observeRecent()

    fun record(event: String, detail: String = "", level: String = "INFO") {
        val entry = PortalLogEntity(level = level, event = event, detail = detail)
        Log.i(TAG, "$event ${if (detail.isBlank()) "" else "- $detail"}")
        scope.launch {
            dao.insert(entry)
            dao.trim(MAX_ROWS)
            _live.tryEmit(entry)
        }
    }

    fun error(event: String, detail: String) = record(event, detail, "ERROR")

    suspend fun clear() = dao.clear()

    companion object {
        private const val TAG = "EventLog"
        const val MAX_ROWS = 1000
    }
}
