package com.credivoice.alertreader

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val green = Color.rgb(7, 134, 95)
    private val navy = Color.rgb(20, 35, 58)
    private val muted = Color.rgb(94, 105, 119)
    private val cream = Color.rgb(248, 246, 242)
    private val cardColor = Color.WHITE
    private var testTts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = cream
        window.navigationBarColor = cream
        if (Build.VERSION.SDK_INT >= 26) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        renderHome()
    }

    override fun onResume() { super.onResume(); renderHome() }

    private fun renderHome() {
        val root = page()
        root.addView(header())
        val accessOkay = notificationAccessGranted()
        if (!accessOkay) {
            val setup = card()
            setup.addView(title("Allow notification access"))
            setup.addView(label("Android grants access to notification content across apps. CrediVoice filters likely credit alerts on this device and does not upload or save full message text."))
            setup.addView(actionButton("Grant notification access") { openNotificationAccessSettings() })
            root.addView(setup)
        }

        val main = card()
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(title("Read aloud"), LinearLayout.LayoutParams(0, -2, 1f))
        val master = Switch(this).apply { isChecked = AlertData.enabled(this@MainActivity); setOnCheckedChangeListener { _, checked -> AlertData.setEnabled(this@MainActivity, checked); renderHome() } }
        top.addView(master)
        main.addView(top)
        main.addView(label(if (AlertData.enabled(this)) "Listening for credit alerts" else "Paused — alerts will not be read or saved"))
        main.addView(label("Notification access: ${if (accessOkay) "Allowed" else "Required"}").apply { setTextColor(if (accessOkay) green else muted) })
        if (!accessOkay) main.addView(actionButton("Open access settings") { openNotificationAccessSettings() })
        main.addView(actionButton("Test voice") { speakTest() })
        root.addView(main)

        val quiet = card()
        val quietTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        quietTop.addView(title("Quiet hours"), LinearLayout.LayoutParams(0, -2, 1f))
        quietTop.addView(Switch(this).apply {
            isChecked = AlertData.quietEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked -> AlertData.setQuietEnabled(this@MainActivity, checked); renderHome() }
        })
        quiet.addView(quietTop)
        quiet.addView(label("${formatMinutes(AlertData.quietStart(this))} – ${formatMinutes(AlertData.quietEnd(this))}"))
        quiet.addView(label("Speaking pauses during these hours."))
        quiet.addView(actionButton("Change schedule") { chooseQuietStart() })
        root.addView(quiet)

        val bank = card()
        bank.addView(title("Banks to monitor"))
        val chosen = AlertData.selectedSenders(this)
        bank.addView(label(if (chosen.isEmpty()) "All detected banks" else "${chosen.size} sender${if (chosen.size == 1) "" else "s"} selected"))
        bank.addView(label("${AlertData.discoveredSenders(this).size} bank sender(s) detected so far"))
        bank.addView(actionButton("Choose banks") { chooseBanks() })
        root.addView(bank)

        val audio = card()
        audio.addView(title("Speech volume"))
        val volumeLine = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        volumeLine.addView(label("Low"))
        val slider = SeekBar(this).apply { max = 100; progress = AlertData.volume(this@MainActivity) }
        volumeLine.addView(slider, LinearLayout.LayoutParams(0, -2, 1f))
        val percent = label("${AlertData.volume(this)}%")
        volumeLine.addView(percent)
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { percent.text = "$progress%"; if (fromUser) AlertData.setVolume(this@MainActivity, progress) }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { }
        })
        audio.addView(volumeLine)
        audio.addView(label("This adjusts spoken alert volume. Device volume still applies."))
        root.addView(audio)

        val historyButton = actionButton("View credit history") { renderHistory() }
        root.addView(historyButton)
        root.addView(label("Matching bank alert notifications are checked on this device. Only short summaries are saved, not full message text.").apply {
            gravity = Gravity.CENTER; setPadding(10, 12, 10, 12); setTextColor(muted)
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun renderHistory() {
        val root = page()
        root.addView(header("Credit history", "Recent credit alerts detected by CrediVoice"))
        root.addView(actionButton("← Back") { renderHome() })
        val events = AlertData.history(this)
        if (events.isEmpty()) {
            val empty = card()
            empty.addView(title("No alerts yet"))
            empty.addView(label("Detected credit alerts will appear here when bank or SMS apps post matching notifications."))
            root.addView(empty)
        } else {
            events.forEach { event ->
                val item = card()
                item.addView(title(event.amount.ifBlank { "Credit alert" }))
                item.addView(label("${event.sender}  •  ${AlertData.formatDate(event.time)}"))
                item.addView(label(if (event.spoken) "Spoken aloud" else "Detected during quiet hours"))
                root.addView(item)
            }
            root.addView(actionButton("Clear history") {
                AlertDialog.Builder(this).setTitle("Clear history?").setMessage("This removes the saved alert summaries from this device.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Clear") { _, _ -> AlertData.clearHistory(this); renderHistory() }.show()
            })
        }
        root.addView(label("History is stored only on this device. Up to 100 summaries are kept; full SMS messages are not saved.").apply { setPadding(12, 8, 12, 20); setTextColor(muted) })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun chooseBanks() {
        val senders = AlertData.discoveredSenders(this).sorted()
        if (senders.isEmpty()) {
            AlertDialog.Builder(this).setTitle("No bank senders detected")
                .setMessage("CrediVoice learns sender IDs when it detects credit alert SMS messages. Until then, all matching credit senders are monitored.")
                .setPositiveButton("OK", null).show()
            return
        }
        val current = AlertData.selectedSenders(this)
        val checked = BooleanArray(senders.size) { current.isEmpty() || senders[it] in current }
        AlertDialog.Builder(this).setTitle("Choose bank senders")
            .setMultiChoiceItems(senders.toTypedArray(), checked) { _, which, isChecked -> checked[which] = isChecked }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val selected = senders.indices.filter { checked[it] }.map { senders[it] }.toSet()
                if (selected.isEmpty()) {
                    Toast.makeText(this, "Select at least one sender, or choose all senders.", Toast.LENGTH_LONG).show()
                } else {
                    AlertData.setSelectedSenders(this, if (selected.size == senders.size) emptySet() else selected)
                    renderHome()
                }
            }.show()
    }

    private fun chooseQuietStart() {
        val start = AlertData.quietStart(this)
        TimePickerDialog(this, { _, hour, minute ->
            val startValue = hour * 60 + minute
            getSharedPreferences("credivoice", MODE_PRIVATE).edit().putInt("quietStart", startValue).apply()
            val end = AlertData.quietEnd(this)
            TimePickerDialog(this, { _, endHour, endMinute ->
                getSharedPreferences("credivoice", MODE_PRIVATE).edit().putInt("quietEnd", endHour * 60 + endMinute).apply()
                renderHome()
            }, end / 60, end % 60, false).show()
        }, start / 60, start % 60, false).show()
    }

    private fun speakTest() {
        if (testTts == null) {
            testTts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    testTts?.language = java.util.Locale.getDefault()
                    speakTestNow()
                } else Toast.makeText(this, "Check your device text-to-speech settings.", Toast.LENGTH_LONG).show()
            }
        } else speakTestNow()
    }

    private fun speakTestNow() {
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, AlertData.volume(this@MainActivity) / 100f) }
        testTts?.speak("CrediVoice is ready to read bank credit alerts aloud.", TextToSpeech.QUEUE_FLUSH, params, "test-voice")
    }

    override fun onDestroy() { testTts?.stop(); testTts?.shutdown(); testTts = null; super.onDestroy() }

    private fun notificationAccessGranted(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        val component = ComponentName(this, CreditNotificationListener::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    private fun openNotificationAccessSettings() {
        try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        catch (_: Exception) { Toast.makeText(this, "Open Android Settings and search for Notification access.", Toast.LENGTH_LONG).show() }
    }

    private fun formatMinutes(value: Int): String {
        val h = value / 60; val m = value % 60
        val suffix = if (h < 12) "AM" else "PM"
        val display = when (h % 12) { 0 -> 12; else -> h % 12 }
        return "%d:%02d %s".format(display, m, suffix)
    }

    private fun page(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(20, 12, 20, 20); setBackgroundColor(cream)
    }

    private fun header(titleText: String = "CrediVoice", subtitle: String = "Bank alerts, spoken clearly"): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(4, 18, 4, 18) }
        box.addView(TextView(this).apply { text = "◖  $titleText"; textSize = 28f; setTextColor(navy); setTypeface(null, Typeface.BOLD) })
        box.addView(label(subtitle))
        return box
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(18, 16, 18, 16)
        background = GradientDrawable().apply { setColor(cardColor); cornerRadius = 22f }
        elevation = 2f
        val params = LinearLayout.LayoutParams(-1, -2); params.setMargins(0, 0, 0, 14); layoutParams = params
    }

    private fun title(text: String) = TextView(this).apply {
        this.text = text; textSize = 20f; setTextColor(navy); setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, 8)
    }

    private fun label(text: String) = TextView(this).apply { this.text = text; textSize = 15f; setTextColor(muted); setPadding(0, 3, 0, 5) }

    private fun actionButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text; setTextColor(green); setOnClickListener { action() }
    }
}
