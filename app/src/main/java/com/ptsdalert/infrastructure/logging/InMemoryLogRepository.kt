package com.ptsdalert.infrastructure.logging

import com.ptsdalert.domain.model.LogEntry
import com.ptsdalert.domain.ports.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class InMemoryLogRepository(private val maxSize: Int = 500) : LogRepository {

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    override suspend fun insert(entry: LogEntry) {
        _entries.update { current ->
            (listOf(entry) + current).take(maxSize)
        }
    }

    override fun observeRecent(limit: Int): Flow<List<LogEntry>> =
        _entries.map { it.take(limit) }
}
