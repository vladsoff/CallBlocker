package com.fundata.callblocker.vibration

import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.fundata.callblocker.data.DiagnosticsStore

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

        if (!nm.isNotificationPolicyAccessGranted) {
            DiagnosticsStore.saveError("DND: нет разрешения", Throwable("isNotificationPolicyAccessGranted = false"))
            return
        }
        if (isSilencedByUs) return

        val currentFilter = nm.currentInterruptionFilter
        if (currentFilter == NotificationManager.INTERRUPTION_FILTER_NONE) {
            DiagnosticsStore.saveError("DND: уже включен пользователем", Throwable("filter = NONE"))
            return
        }

        previousInterruptionFilter = currentFilter
        isSilencedByUs = true

        try {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            DiagnosticsStore.saveError("DND: ВКЛЮЧЕНА ТИШИНА", Throwable("было: $currentFilter, таймер: ${timeoutMs}мс"))
        } catch (t: Throwable) {
            DiagnosticsStore.saveError("DND: ошибка включения", t)
            isSilencedByUs = false
            return
        }

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
                    try {
                        nm.setInterruptionFilter(prev)
                        DiagnosticsStore.saveError("DND: звук возвращён", Throwable("режим: $prev"))
                    } catch (t: Throwable) {
                        DiagnosticsStore.saveError("DND: ошибка возврата", t)
                    }
                }
            }
        }
        previousInterruptionFilter = null
        isSilencedByUs = false
    }
}
