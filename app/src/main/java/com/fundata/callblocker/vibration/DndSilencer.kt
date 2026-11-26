package com.fundata.callblocker.vibration

import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper

class DndSilencer private constructor(context: Context) {

    private val notificationManager =
        context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val handler = Handler(Looper.getMainLooper())
    private var previousInterruptionFilter: Int? = null
    private var isSilencedByUs = false

    private val restoreRunnable = Runnable {
        restore()
    }

    fun silenceTemporarily(timeoutMs: Long = 2000L) {
        if (!notificationManager.isNotificationPolicyAccessGranted) return
        if (isSilencedByUs) return

        val currentFilter = notificationManager.currentInterruptionFilter
        if (currentFilter == NotificationManager.INTERRUPTION_FILTER_NONE) return

        previousInterruptionFilter = currentFilter
        isSilencedByUs = true

        // Включаем полную тишину — часы мгновенно перестают получать вызовы
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)

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
        previousInterruptionFilter?.let { prev ->
            if (notificationManager.isNotificationPolicyAccessGranted) {
                notificationManager.setInterruptionFilter(prev)
            }
        }
        previousInterruptionFilter = null
        isSilencedByUs = false
    }

    companion object {
        @Volatile
        private var instance: DndSilencer? = null

        fun getInstance(context: Context): DndSilencer {
            return instance ?: synchronized(this) {
                instance ?: DndSilencer(context).also { instance = it }
            }
        }
    }
}
