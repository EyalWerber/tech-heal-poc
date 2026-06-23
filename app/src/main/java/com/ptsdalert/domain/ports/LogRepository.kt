package com.ptsdalert.domain.ports

import com.ptsdalert.domain.model.LogEntry
import kotlinx.coroutines.flow.Flow

interface LogRepository {
    suspend fun insert(entry: LogEntry)
    fun observeRecent(limit: Int = 100): Flow<List<LogEntry>>
}
