package com.ptsdalert.infrastructure.logging

import android.util.Log
import com.ptsdalert.domain.model.LogEntry
import com.ptsdalert.domain.model.LogLevel
import com.ptsdalert.domain.ports.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

object AppLogger {

    private var repo: LogRepository = InMemoryLogRepository()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun init(repository: LogRepository) {
        repo = repository
    }

    fun d(tag: String, message: String) = write(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = write(LogLevel.INFO,  tag, message)
    fun w(tag: String, message: String) = write(LogLevel.WARN,  tag, message)
    fun e(tag: String, message: String) = write(LogLevel.ERROR, tag, message)

    fun observeRecent(limit: Int = 100): Flow<List<LogEntry>> = repo.observeRecent(limit)

    private fun write(level: LogLevel, tag: String, message: String) {
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message)
            LogLevel.INFO  -> Log.i(tag, message)
            LogLevel.WARN  -> Log.w(tag, message)
            LogLevel.ERROR -> Log.e(tag, message)
        }
        scope.launch { repo.insert(LogEntry(System.currentTimeMillis(), level, tag, message)) }
    }
}
