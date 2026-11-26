import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper

class DndSilencer(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val handler = Handler(Looper.getMainLooper())
    private var previousInterruptionFilter: Int? = null
    private var isSilencedByUs = false

    private val restoreRunnable = Runnable {
        restore()
    }

    /**
     * Включает полную тишину (DND) на указанное время (по умолчанию 2000 мс),
     * чтобы часы не успели получить уведомление до считывания текста.
     */
    fun silenceTemporarily(timeoutMs: Long = 2000L) {
        if (!notificationManager.isNotificationPolicyAccessGranted) return
        if (isSilencedByUs) return

        val currentFilter = notificationManager.currentInterruptionFilter
        // Если пользователь уже сам включил полный DND, ничего не трогаем
        if (currentFilter == NotificationManager.INTERRUPTION_FILTER_NONE) return

        previousInterruptionFilter = currentFilter
        isSilencedByUs = true

        // INTERRUPTION_FILTER_NONE блокирует звук, вибрацию и пуши на Bluetooth-часы
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)

        // Страховочный таймер: если спам-метка не пришла за 2 сек, возвращаем звук
        handler.removeCallbacks(restoreRunnable)
        handler.postDelayed(restoreRunnable, timeoutMs)
    }

    /**
     * Возвращает исходный режим уведомлений.
     * Вызывать при:
     * 1) Истечении таймера (звонок признан нормальным)
     * 2) Сбросе спам-звонка (с небольшой задержкой ~500 мс, чтобы хвост звонка не прорвался)
     * 3) Переходе телефона в состояние IDLE (звонящий сам положил трубку)
     */
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
}
