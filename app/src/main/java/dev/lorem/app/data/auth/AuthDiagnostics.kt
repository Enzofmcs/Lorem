package dev.lorem.app.data.auth

import android.util.Log

/** Controlled stage names and SDK error codes only. Never pass tokens, accounts or exception messages. */
internal fun authLog(event: String) {
    Log.i("LoremAuth", event)
}
