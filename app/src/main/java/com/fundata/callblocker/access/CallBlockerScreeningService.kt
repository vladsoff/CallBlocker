package com.fundata.callblocker.access

import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallScreeningService
import com.fundata.callblocker.data.AppSettings
import com.fundata.callblocker.data.DbHelper
import com.fundata.callblocker.data.DiagnosticsStore
import com.fundata.callblocker.data.RuleMatcher

class CallBlockerScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        // Собираем полный дамп всех полей, которые прислал оператор/система
        val dump = buildString {
            append("handle=${callDetails.handle} | ")
            append("displayName='${callDetails.callerDisplayName}' | ")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                append("contactName='${callDetails.contactDisplayName}' | ")
                append("verif=${callDetails.callerNumberVerificationStatus} | ")
            }
            append("hints='${callDetails.statusHints?.label}' | ")
            append("extras=[")
            callDetails.extras?.let { b ->
                for (k in b.keySet()) {
                    @Suppress("DEPRECATION")
                    append("$k=${b.get(k)}; ")
                }
            }
            append("] | intentExtras=[")
            callDetails.intentExtras?.let { b ->
                for (k in b.keySet()) {
                    @Suppress("DEPRECATION")
                    append("$k=${b.get(k)}; ")
                }
            }
            append("]")
        }

        // Пишем дамп в лог диагностики, чтобы посмотреть глазами
        DiagnosticsStore.saveError("SCREENING DUMP", Throwable(dump))

        if (!AppSettings.isProtectionEnabled(this)) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        // Собираем весь найденный текст из Call.Details для проверки по правилам
        val candidateTexts = mutableSetOf<String>()
        callDetails.callerDisplayName?.takeIf { it.isNotBlank() }?.let { candidateTexts.add(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            callDetails.contactDisplayName?.takeIf { it.isNotBlank() }?.let { candidateTexts.add(it) }
        }
        collectBundleStrings(callDetails.extras, candidateTexts)
        collectBundleStrings(callDetails.intentExtras, candidateTexts)

        val db = DbHelper(this)

        // 1. Проверяем правила по тексту прямо внутри CallScreeningService!
        val normalizedTexts = candidateTexts.map(RuleMatcher::normalizeText).filter { it.isNotEmpty() }
        val blockedText = db.valuesByType(DbHelper.TYPE_TEXT).firstOrNull { blocked ->
            val pattern = RuleMatcher.normalizeText(blocked)
            normalizedTexts.any { value -> RuleMatcher.wildcardMatches(pattern, value) }
        }
        if (blockedText != null) {
            DiagnosticsStore.saveDecision(this, "Screening: МГНОВЕННО заблокирован по тексту: $blockedText")
            rejectSilently(callDetails)
            return
        }

        // 2. Проверяем правила по номеру телефона
        val rawNumber = callDetails.handle?.schemeSpecificPart.orEmpty()
        val normalizedPhone = RuleMatcher.normalizePhone(rawNumber)
        if (normalizedPhone.length >= 5) {
            val blockedPhone = db.valuesByType(DbHelper.TYPE_PHONE).firstOrNull { blocked ->
                val pattern = RuleMatcher.normalizePhonePattern(blocked)
                pattern.isNotEmpty() && RuleMatcher.wildcardMatches(pattern, normalizedPhone)
            }
            if (blockedPhone != null) {
                DiagnosticsStore.saveDecision(this, "Screening: МГНОВЕННО заблокирован по номеру: $blockedPhone")
                rejectSilently(callDetails)
                return
            }
        }

        // Убрали задержку 1800мс, чтобы не тормозить звонок, если текст придёт позже
        respondToCall(callDetails, CallResponse.Builder().build())
    }

    private fun rejectSilently(callDetails: Call.Details) {
        val response = CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipCallLog(false)
            .setSkipNotification(true) // Часы вообще не узнают о звонке
            .build()
        respondToCall(callDetails, response)
    }

    private fun dumpBundle(bundle: Bundle?, sb: StringBuilder) {
        if (bundle == null || bundle.isEmpty) {
            sb.appendLine("(пусто)")
            return
        }
        for (key in bundle.keySet()) {
            @Suppress("DEPRECATION")
            val value = bundle.get(key)
            sb.appendLine("$key = $value")
        }
    }

    private fun collectBundleStrings(bundle: Bundle?, out: MutableSet<String>) {
        if (bundle == null) return
        for (key in bundle.keySet()) {
            @Suppress("DEPRECATION")
            val value = bundle.get(key)
            if (value is CharSequence && value.isNotBlank()) {
                out.add(value.toString())
            }
        }
    }
}
