package com.fundata.callblocker.vibration

import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper

object DndSilencer {

    private val handler = Handler(Looper.getMainLooper())
    private var previousInterruptionFilter: Int? = null
    private var isSilencedByUs = false
    private var appContext: Context? = null

    private val restoreRunnable = Runnable {
        restore()
    }

    fun silenceTemporarily(context: Context, timeoutMs: Long = 2500L) {
        val ctx = context.applicationContext
        appContext = ctx
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!nm.isNotificationPolicyAccessGranted) return
        if (isSilencedByUs) return

        val currentFilter = nm.currentInterruptionFilter
        if (currentFilter == NotificationManager.INTERRUPTION_FILTER_NONE) return

        previousInterruptionFilter = currentFilter
        isSilencedByUs = true

        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)

        handler.removeCallbacks(restoreRunnable)
        handler.postDelayed(restoreRunnable, timeoutMs)
    }

    fun restore(delayMs: Long = 0L) {
        handler.removeCallbacks(restoreRunnable)
        if (!isSilencedByUs) return

        if (delayMs > 0) {
            handler.postDelayed({ executeRestore() }, delayMs)
        } else {
            executeRestore()
        }
    }

    private fun executeRestore() {
        if (!isSilencedByUs) return
        val ctx = appContext
        if (ctx != null) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            previousInterruptionFilter?.let { prev ->
                if (nm.isNotificationPolicyAccessGranted) {
                    nm.setInterruptionFilter(prev)
                }
            }
        }
        previousInterruptionFilter = null
        isSilencedByUs = false
    }
}
