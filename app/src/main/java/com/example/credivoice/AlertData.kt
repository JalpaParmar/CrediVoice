package com.credivoice.alertreader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CreditEvent(val time: Long, val amount: String, val sender: String, val spoken: Boolean)

object AlertData {
    private const val PREFS = "credivoice"
    private const val HISTORY = "history"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context) = prefs(context).getBoolean("enabled", true)
    fun setEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean("enabled", value).apply()
    fun quietEnabled(context: Context) = prefs(context).getBoolean("quiet", true)
    fun setQuietEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean("quiet", value).apply()
    fun quietStart(context: Context) = prefs(context).getInt("quietStart", 22 * 60)
    fun quietEnd(context: Context) = prefs(context).getInt("quietEnd", 7 * 60)
    fun volume(context: Context) = prefs(context).getInt("volume", 75)
    fun setVolume(context: Context, value: Int) = prefs(context).edit().putInt("volume", value).apply()
    fun selectedSenders(context: Context): Set<String> = prefs(context).getStringSet("senders", emptySet())?.toSet() ?: emptySet()
    fun setSelectedSenders(context: Context, value: Set<String>) = prefs(context).edit().putStringSet("senders", value).apply()
    fun discoveredSenders(context: Context): Set<String> = prefs(context).getStringSet("discovered", emptySet())?.toSet() ?: emptySet()
    fun addDiscoveredSender(context: Context, sender: String) {
        if (sender.isBlank()) return
        val all = discoveredSenders(context) + sender
        prefs(context).edit().putStringSet("discovered", all).apply()
    }

    fun isDuplicate(context: Context, sender: String, message: String, now: Long): Boolean {
        val normalized = "${sender.trim().lowercase()}|${message.trim().lowercase().replace(Regex("\\s+"), " ")}"
        val fingerprint = java.security.MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray()).joinToString("") { "%02x".format(it) }
        val p = prefs(context)
        val previous = p.getString("lastFingerprint", "")
        val previousTime = p.getLong("lastFingerprintAt", 0L)
        val duplicate = previous == fingerprint && now - previousTime in 0..15_000
        if (!duplicate) p.edit().putString("lastFingerprint", fingerprint).putLong("lastFingerprintAt", now).apply()
        return duplicate
    }

    fun withinQuietHours(context: Context): Boolean {
        if (!quietEnabled(context)) return false
        val now = java.util.Calendar.getInstance()
        val minute = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        val start = quietStart(context)
        val end = quietEnd(context)
        return if (start <= end) minute in start until end else minute >= start || minute < end
    }

    fun addEvent(context: Context, event: CreditEvent) {
        val all = history(context).toMutableList()
        all.add(0, event)
        val json = JSONArray()
        all.take(100).forEach { item ->
            json.put(JSONObject().put("time", item.time).put("amount", item.amount).put("sender", item.sender).put("spoken", item.spoken))
        }
        prefs(context).edit().putString(HISTORY, json.toString()).apply()
    }

    fun history(context: Context): List<CreditEvent> {
        val raw = prefs(context).getString(HISTORY, "[]") ?: "[]"
        return try {
            val json = JSONArray(raw)
            (0 until json.length()).map { i ->
                val o = json.getJSONObject(i)
                CreditEvent(o.getLong("time"), o.getString("amount"), o.getString("sender"), o.getBoolean("spoken"))
            }
        } catch (_: Exception) { emptyList() }
    }

    fun clearHistory(context: Context) = prefs(context).edit().remove(HISTORY).apply()

    fun formatDate(time: Long): String = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(time))
}
