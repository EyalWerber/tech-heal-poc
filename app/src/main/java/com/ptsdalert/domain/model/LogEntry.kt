package com.ptsdalert.domain.model

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val id: Long = 0
)
