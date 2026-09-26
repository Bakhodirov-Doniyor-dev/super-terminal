package com.example.terminal

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface defining a persistent interactive terminal session.
 * Replaces the per-command execution model with a continuous, persistent process
 * that maintains working directory (CWD), environment variables, subshells,
 * and handles raw stdin/stdout/stderr streaming.
 */
interface TerminalSession {
    val id: String
    val title: String
    val isAlive: Boolean
    val currentWorkingDir: StateFlow<String>

    fun start(onOutput: (String) -> Unit)
    fun addOutputListener(listener: (String) -> Unit) {}
    fun removeOutputListener(listener: (String) -> Unit) {}
    fun writeInput(input: String)
    fun sendInterrupt() // Ctrl+C (\u0003)
    fun sendEof()       // Ctrl+D (\u0004)
    fun resize(cols: Int, rows: Int)
    fun close()
}
