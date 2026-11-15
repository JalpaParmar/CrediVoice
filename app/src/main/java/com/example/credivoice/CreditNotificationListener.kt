package com.credivoice.alertreader

import android.app.Notification
import android.media.AudioAttributes
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import java.util.Locale

class CreditNotificationListener : NotificationListenerService(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val pendingSpeech = mutableListOf<Pair<String, String>>()

    private data class DirectionMark(
        val direction: String,
        val word: String,
        val start: Int,
        val end: Int
    )

    private data class AmountCandidate(
        val amount: String,
        val start: Int,
        val end: Int
    )

    private data class ParsedTransaction(val amount: String, val direction: String)

    private val directionPattern = Regex(
        """(?i)\b(credited|credit|deposited|deposit|received|added|cr|debited|debit|withdrawn|withdrawal|withdraw|spent|paid|purchase|sent|dr)\b"""
    )

    private val amountPattern = Regex(
        """(?i)(?:₹\s*|INR\s*|Rs\.?\s*)([0-9][0-9,]*(?:\.[0-9]{1,2})?)|([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:Rs\.?|INR|₹)|\b(?:CR|DR)\b\s*[:\-]?\s*(?:(?:₹|INR\s*|Rs\.?\s*))?([0-9][0-9,]*(?:\.[0-9]{1,2})?)|([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*\b(?:CR|DR)\b"""
    )

    private val genericTitles = setOf(
        "amount received", "credit alert", "debit alert", "transaction alert",
        "payment received", "account alert", "new notification", "notification"
    )

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (tts == null) tts = TextToSpeech(applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val engine = tts ?: return
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        if (engine.setLanguage(Locale("en", "IN")) < TextToSpeech.LANG_AVAILABLE) return
        ttsReady = true
        pendingSpeech.toList().forEach { (text, id) -> speakNow(text, id) }
        pendingSpeech.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || !AlertData.enabled(this)) return
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val contentParts = notificationTextParts(extras)
        val fullMessage = (listOf(title) + contentParts).filter { it.isNotBlank() }.distinct().joinToString(". ")
        if (fullMessage.isBlank()) return

        val transactions = parseTransactions(fullMessage)
        if (transactions.isEmpty()) return

        val sourceName = getSourceName(sbn.packageName)
        val titleSender = title.takeIf { !isGenericTitle(it) }
        val senderCandidates = listOfNotNull(titleSender, sourceName).distinct()
        senderCandidates.forEach { AlertData.addDiscoveredSender(this, it) }

        val selected = AlertData.selectedSenders(this)
        val selectedMatch = senderCandidates.firstOrNull { candidate ->
            selected.any { it.equals(candidate, ignoreCase = true) }
        }
        if (selected.isNotEmpty() && selectedMatch == null) return
        val sender = selectedMatch ?: titleSender ?: sourceName

        val now = System.currentTimeMillis()
        if (AlertData.isDuplicate(this, sender, fullMessage, now)) return

        transactions.forEachIndexed { index, transaction ->
            val speech = if (transaction.direction == "credit") {
                "${transaction.amount} credited from $sender."
            } else {
                "${transaction.amount} debited from $sender."
            }
            AlertData.addEvent(
                this,
                CreditEvent(now + index, transaction.amount, sender, ttsReady, transaction.direction)
            )
            if (ttsReady) speak(speech, "bank-alert-$now-$index")
        }
    }

    private fun notificationTextParts(extras: Bundle): List<String> {
        val parts = mutableListOf<String>()
        listOf(
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
            Notification.EXTRA_INFO_TEXT
        ).forEach { key ->
            extras.getCharSequence(key)?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(parts::add)
        }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { line ->
            line?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(parts::add)
        }
        extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.forEach { message ->
            val messageBundle = message as? Bundle ?: return@forEach
            messageBundle.getCharSequence("text")?.toString()?.trim()
                ?.takeIf { it.isNotBlank() }?.let(parts::add)
        }
        return parts.distinct()
    }

    private fun parseTransactions(message: String): List<ParsedTransaction> {
        val marks = directionPattern.findAll(message).map { match ->
            val word = match.value.lowercase(Locale.ROOT)
            DirectionMark(
                direction = if (word in setOf("cr", "credit", "credited", "deposit", "deposited", "received", "added")) "credit" else "debit",
                word = word,
                start = match.range.first,
                end = match.range.last + 1
            )
        }.toList()
        if (marks.isEmpty()) return emptyList()

        val amounts = amountPattern.findAll(message).mapNotNull { match ->
            val number = (1..4).firstNotNullOfOrNull { group -> match.groups[group]?.value }
                ?: return@mapNotNull null
            AmountCandidate(number, match.range.first, match.range.last + 1)
        }.toList()
        if (amounts.isEmpty()) return emptyList()

        val singleDirectionType = marks.map { it.direction }.distinct().size == 1
        return amounts.mapNotNull { amount ->
            val nearby = marks.map { mark -> mark to distance(amount.start, amount.end, mark.start, mark.end) }
                .filter { (_, gap) -> gap <= if (amounts.size == 1 && singleDirectionType) 90 else 45 }
            if (nearby.isEmpty()) return@mapNotNull null

            val nearestDistance = nearby.minOf { it.second }
            val nearest = nearby.filter { it.second == nearestDistance }
            if (nearest.map { it.first.direction }.distinct().size != 1) return@mapNotNull null
            val mark = nearest.first().first

            val contextStart = (amount.start - 30).coerceAtLeast(0)
            val contextEnd = (amount.end + 30).coerceAtMost(message.length)
            val nearbyText = message.substring(contextStart, contextEnd).lowercase(Locale.ROOT)
            if (Regex("""\b(?:balance|bal|available|avail|ledger|closing|opening)\b""").containsMatchIn(nearbyText) && nearestDistance > 18) {
                return@mapNotNull null
            }
            if (mark.word == "credit" && Regex("""\bcredit\s+card\b""").containsMatchIn(nearbyText)) return@mapNotNull null
            if (mark.word == "debit" && Regex("""\bdebit\s+card\b""").containsMatchIn(nearbyText)) return@mapNotNull null

            ParsedTransaction("₹${amount.amount}", mark.direction)
        }.distinctBy { it.direction to it.amount }
    }

    private fun distance(firstStart: Int, firstEnd: Int, secondStart: Int, secondEnd: Int): Int = when {
        firstEnd < secondStart -> secondStart - firstEnd
        secondEnd < firstStart -> firstStart - secondEnd
        else -> 0
    }

    private fun isGenericTitle(title: String): Boolean =
        title.lowercase(Locale.ROOT).replace(Regex("""[^a-z ]"""), " ").trim().replace(Regex("""\s+"""), " ") in genericTitles

    private fun speak(text: String, id: String) {
        if (ttsReady) speakNow(text, id) else pendingSpeech.add(text to id)
    }

    private fun speakNow(text: String, id: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, id)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private fun getSourceName(source: String): String = try {
        val app = packageManager.getApplicationInfo(source, 0)
        packageManager.getApplicationLabel(app).toString()
    } catch (_: Exception) {
        source
    }
}
