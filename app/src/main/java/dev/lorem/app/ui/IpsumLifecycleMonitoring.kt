package dev.lorem.app.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle

/** Kept outside rendering so lifecycle and virtual-clock tests exercise the same binding. */
suspend fun monitorResumedIpsum(lifecycle: Lifecycle, ipsumId: Long, monitor: suspend (Long) -> Unit) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { monitor(ipsumId) }
}
