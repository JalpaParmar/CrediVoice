package com.credivoice.alertreader

import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.regex.Pattern

class CreditNotificationListener : NotificationListenerService(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val pendingSpeech = mutableListOf<String>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (tts == null) tts = TextToSpeech(applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        ttsReady = true
        tts?.language = Locale.getDefault()
        pendingSpeech.toList().forEach(::speakNow)
        pendingSpeech.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || !AlertData.enabled(this)) return
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString()?.trim().orEmpty()
        val text = buildString {
            extras.getCharSequence("android.text")?.toString()?.let { append(it).append(' ') }
            extras.getCharSequence("android.bigText")?.toString()?.let { append(it).append(' ') }
            extras.getCharSequence("android.subText")?.toString()?.let { append(it) }
        }.trim()
        val message = listOf(title, text).filter { it.isNotBlank() }.joinToString(". ")
        if (message.isBlank() || !looksLikeCredit(message)) return

        val sender = title.ifBlank { getSourceName(sbn.packageName) }
        AlertData.addDiscoveredSender(this, sender)
        val selected = AlertData.selectedSenders(this)
        if (selected.isNotEmpty() && sender !in selected) return

        val now = System.currentTimeMillis()
        if (AlertData.isDuplicate(this, sender, text, now)) return
        val amount = extractAmount(message).orEmpty()
        val speakNow = !AlertData.withinQuietHours(this)
        val speech = if (amount.isNotBlank()) "Credit alert. $amount credited. From $sender."
            else "A credit alert was received from $sender."
        AlertData.addEvent(this, CreditEvent(now, amount, sender, speakNow))
        if (speakNow) speak(speech)
    }

    private fun speak(text: String) { if (ttsReady) speakNow(text) else pendingSpeech.add(text) }

    private fun speakNow(text: String) {
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, AlertData.volume(this@CreditNotificationListener) / 100f) }
        tts?.speak(text, TextToSpeech.QUEUE_ADD, params, "credit-alert-${System.currentTimeMillis()}")
    }

    override fun onDestroy() { tts?.stop(); tts?.shutdown(); tts = null; super.onDestroy() }

    private fun getSourceName(source: String): String = try {
        val app = packageManager.getApplicationInfo(source, 0)
        packageManager.getApplicationLabel(app).toString()
    } catch (_: Exception) { source }

    private fun looksLikeCredit(message: String): Boolean {
        val text = message.lowercase(Locale.ROOT)
        if (listOf("debited", "withdrawn", "spent", "purchase", "paid", "sent", "debit").any(text::contains)) return false
        return listOf("credited", "credit alert", "deposited", "credited to", "received in your", "amount received").any(text::contains)
    }

    private fun extractAmount(message: String): String? {
        val pattern = Pattern.compile("(?i)(?:₹|INR\\s?|Rs\\.?\\s?)([0-9,]+(?:\\.[0-9]{1,2})?)")
        val matcher = pattern.matcher(message)
        return if (matcher.find()) "₹${matcher.group(1)}" else null
    }
}
