package com.fundata.callblocker.access

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.core.content.ContextCompat
import com.fundata.callblocker.data.AppSettings
import com.fundata.callblocker.data.DbHelper
import com.fundata.callblocker.data.DiagnosticsStore
import com.fundata.callblocker.data.RuleMatcher

class CallBlockerScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        if (!AppSettings.isProtectionEnabled(this)) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val rawNumber = callDetails.handle?.schemeSpecificPart.orEmpty()
        val normalizedPhone = RuleMatcher.normalizePhone(rawNumber)

        // 1. Проверяем правила по номеру телефона (мгновенный сброс без уведомления часов)
        if (normalizedPhone.length >= 5) {
            val db = DbHelper(this)
            val blockedPhone = db.valuesByType(DbHelper.TYPE_PHONE).firstOrNull { blocked ->
                val pattern = RuleMatcher.normalizePhonePattern(blocked)
                pattern.isNotEmpty() && RuleMatcher.wildcardMatches(pattern, normalizedPhone)
            }
            if (blockedPhone != null) {
                DiagnosticsStore.saveDecision(this, "Screening: заблокирован по номеру $blockedPhone")
                rejectSilently(callDetails)
                return
            }
        }

        // 2. Если номер есть в контактах — пропускаем мгновенно без задержки
        if (rawNumber.isNotBlank() && isNumberInContacts(rawNumber)) {
            DiagnosticsStore.saveDecision(this, "Screening: номер в контактах, пропущен сразу")
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        // 3. Незнакомый номер: ставим вызов на паузу на 1800 мс, ждем текст от Accessibility
        DiagnosticsStore.saveError("Screening: удержание звонка 1800мс", Throwable("номер: $rawNumber"))
        ScreeningCoordinator.holdCall(this, callDetails, 1800L)
    }

    private fun rejectSilently(callDetails: Call.Details) {
        val response = CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipCallLog(false)
            .setSkipNotification(true) // Ключевой флаг: Bluetooth-часы не получают сигнал звонка
            .build()
        respondToCall(callDetails, response)
    }

    private fun isNumberInContacts(phoneNumber: String): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (_: Throwable) {
            false
        }
    }
}

/**
 * Координатор между CallScreeningService и CallUiAccessibilityService
 */
object ScreeningCoordinator {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingService: CallScreeningService? = null
    private var pendingDetails: Call.Details? = null

    private val releaseRunnable = Runnable {
        allowPendingCall()
    }

    fun holdCall(service: CallScreeningService, details: Call.Details, timeoutMs: Long) {
        handler.removeCallbacks(releaseRunnable)
        pendingService = service
        pendingDetails = details
        handler.postDelayed(releaseRunnable, timeoutMs)
    }

    /**
     * Вызывается из CallUiAccessibilityService, если найден запрещенный текст
     */
    fun rejectPendingCallIfActive(): Boolean {
        handler.removeCallbacks(releaseRunnable)
        val service = pendingService ?: return false
        val details = pendingDetails ?: return false
        pendingService = null
        pendingDetails = null

        val response = CallScreeningService.CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipCallLog(false)
            .setSkipNotification(true)
            .build()
        service.respondToCall(details, response)
        return true
    }

    fun allowPendingCall() {
        handler.removeCallbacks(releaseRunnable)
        val service = pendingService ?: return
        val details = pendingDetails ?: return
        pendingService = null
        pendingDetails = null

        service.respondToCall(details, CallScreeningService.CallResponse.Builder().build())
    }
}
