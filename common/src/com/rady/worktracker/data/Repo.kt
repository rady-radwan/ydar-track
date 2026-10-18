package com.rady.worktracker.data

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single place the app's data lives. The screens and the timer
 * notification/alarm all read and change data through here, so they
 * always agree with each other.
 */
object Repo {
    private lateinit var store: Store
    private val saveLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    @Volatile private var ready = false

    @Synchronized
    fun init(dir: File) {
        if (ready) return
        store = Store(dir)
        _data.value = store.load()
        ready = true
    }

    val current: AppData get() = _data.value

    /** Applies a change and saves it in the background. */
    fun update(transform: (AppData) -> AppData) {
        synchronized(this) {
            _data.value = transform(_data.value)
        }
        scope.launch {
            // Always write the newest state, so saves that finish out of order can't lose changes.
            saveLock.withLock { store.save(_data.value) }
        }
    }

    /** Saves immediately (used by the alarm receiver, which may be killed right after). */
    fun updateNow(transform: (AppData) -> AppData) {
        synchronized(this) {
            _data.value = transform(_data.value)
            store.save(_data.value)
        }
    }
}
