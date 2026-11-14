package com.credivoice.alertreader

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val coral = Color.rgb(255, 56, 92)
    private val ink = Color.rgb(34, 34, 34)
    private val secondary = Color.rgb(113, 113, 113)
    private val pageColor = Color.rgb(255, 249, 247)
    private val blush = Color.rgb(255, 237, 239)
    private val border = Color.rgb(238, 235, 233)
    private var testTts: TextToSpeech? = null
    private var activityTtsInitialized = false
    private var activityTtsReady = false
    private var pendingActivitySpeech: Pair<String, String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = pageColor
        window.navigationBarColor = Color.WHITE
        if (Build.VERSION.SDK_INT >= 26) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        renderHome()
    }

    override fun onResume() {
        super.onResume()
        renderHome()
    }

    private fun renderHome() {
        val content = pageColumn()
        content.addView(brandHeader())
        content.addView(heading("Money updates,\nspoken clearly", "A little more peace of mind, wherever you are."))
        val accessOkay = notificationAccessGranted()

        if (!accessOkay) {
            val setup = card()
            setup.addView(sectionTitle("One quick setup"))
            setup.addView(body("Allow notification access so CrediVoice can find credit alerts. Matching happens on this device."))
            setup.addView(textAction("Allow notification access  →") { openNotificationAccessSettings() })
            content.addView(setup, sectionParams())
        }

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(20))
            background = rounded(coral, 24)
        }
        val heroTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heroTop.addView(TextView(this).apply {
            text = "VOICE ALERTS"
            textSize = 13f
            letterSpacing = 0.08f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        val alertSwitch = accentSwitch(AlertData.enabled(this), coral, Color.WHITE) { checked ->
            AlertData.setEnabled(this, checked)
            renderHome()
        }
        heroTop.addView(alertSwitch)
        hero.addView(heroTop)
        hero.addView(TextView(this).apply {
            text = if (AlertData.enabled(this@MainActivity)) "Listening for credit alerts" else "Voice alerts are paused"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(5))
        })
        hero.addView(TextView(this).apply {
            text = if (accessOkay) "Only matching notifications are read aloud" else "Allow notification access to get started"
            textSize = 14f
            setTextColor(Color.WHITE)
            alpha = 0.92f
        })
        content.addView(hero, sectionParams())

        val bank = card()
        val bankRow = horizontalRow()
        bankRow.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(sectionTitle("Banks monitored"))
            val chosen = AlertData.selectedSenders(this@MainActivity)
            val discovered = AlertData.discoveredSenders(this@MainActivity).sorted()
            addView(body(when {
                chosen.isNotEmpty() -> "${chosen.size} sender${if (chosen.size == 1) "" else "s"} selected"
                discovered.isNotEmpty() -> "${discovered.size} sender${if (discovered.size == 1) "" else "s"} detected"
                else -> "All matching senders"
            }))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        bankRow.addView(textAction("Manage  →") { chooseBanks() })
        bank.addView(bankRow)
        val senders = AlertData.selectedSenders(this).ifEmpty { AlertData.discoveredSenders(this).sorted().take(3).toSet() }
        if (senders.isNotEmpty()) {
            val chips = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(9), 0, 0)
            }
            senders.take(3).forEach { sender ->
                val chip = TextView(this).apply {
                    text = sender
                    textSize = 13f
                    setTextColor(ink)
                    setTypeface(null, Typeface.BOLD)
                    setPadding(dp(13), dp(8), dp(13), dp(8))
                    background = rounded(blush, 14)
                }
                val lp = LinearLayout.LayoutParams(-2, -2)
                lp.marginEnd = dp(8)
                chips.addView(chip, lp)
            }
            bank.addView(chips)
        }
        content.addView(bank, sectionParams())

        val voice = card()
        voice.addView(sectionTitle("Voice language"))
        voice.addView(TextView(this).apply {
            text = "English (India)"
            textSize = 16f
            setTextColor(ink)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(7), 0, dp(2))
        })
        voice.addView(body("Spoken alerts use your phone’s media volume."))
        content.addView(voice, sectionParams())

        content.addView(filledAction("▶    Test voice") { speakTest() }, sectionParams())
        content.addView(TextView(this).apply {
            text = "Your alert summaries stay on this device. Full message text is not saved."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(secondary)
            setPadding(dp(8), dp(4), dp(8), dp(12))
        })
        showScreen(content, "home")
    }

    private fun renderHistory() {
        val content = pageColumn()
        content.addView(brandHeader())
        content.addView(heading("Credit history", "Your recent updates"))
        val events = AlertData.history(this)
        if (events.isEmpty()) {
            val empty = card()
            empty.addView(sectionTitle("No alerts yet"))
            empty.addView(body("Matching credit alerts will appear here after a bank or messaging app posts a notification."))
            content.addView(empty, sectionParams())
        } else {
            events.forEach { event ->
                val item = card()
                val row = horizontalRow()
                val marker = TextView(this).apply {
                    text = "↙"
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setTextColor(coral)
                    background = rounded(blush, 50)
                }
                row.addView(marker, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })
                val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                details.addView(TextView(this).apply {
                    text = event.sender.ifBlank { "Credit alert" }
                    textSize = 16f
                    setTextColor(ink)
                    setTypeface(null, Typeface.BOLD)
                })
                details.addView(body(AlertData.formatDate(event.time)))
                details.addView(body(if (event.spoken) "Spoken aloud" else "Detected during quiet hours"))
                row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(TextView(this).apply {
                    text = event.amount.ifBlank { "Credited" }
                    textSize = 16f
                    setTextColor(ink)
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                })
                item.addView(row)
                item.isClickable = true
                item.isFocusable = true
                item.setOnClickListener { speakHistoryEvent(event) }
                content.addView(item, sectionParams())
            }
            content.addView(textAction("Clear history") {
                AlertDialog.Builder(this)
                    .setTitle("Clear history?")
                    .setMessage("This removes the saved alert summaries from this device.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Clear") { _, _ ->
                        AlertData.clearHistory(this)
                        renderHistory()
                    }.show()
            })
        }
        content.addView(TextView(this).apply {
            text = "History is stored only on this device. Up to 100 short summaries are kept."
            textSize = 12f
            setTextColor(secondary)
            setPadding(0, dp(16), 0, dp(20))
        })
        showScreen(content, "history")
    }

    private fun showScreen(content: LinearLayout, selected: String) {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageColor)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val holder = FrameLayout(this)
        val availableWidth = resources.displayMetrics.widthPixels - dp(32)
        val contentWidth = minOf(dp(560), availableWidth).coerceAtLeast(dp(280))
        val centered = FrameLayout.LayoutParams(contentWidth, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
        }
        val wrapper = FrameLayout(this).apply {
            addView(content, centered)
        }
        scroll.addView(wrapper, android.view.ViewGroup.LayoutParams(-1, -2))
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        shell.addView(bottomNavigation(selected), LinearLayout.LayoutParams(-1, dp(72)))
        setContentView(shell)
    }

    private fun bottomNavigation(selected: String): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(1), border)
            }
        }
        fun item(label: String, glyph: String, tab: String, action: () -> Unit): View {
            val active = selected == tab
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
                val icon = TextView(this@MainActivity).apply {
                    text = glyph
                    textSize = 20f
                    gravity = Gravity.CENTER
                    setTextColor(if (active) coral else secondary)
                }
                addView(icon)
                addView(TextView(this@MainActivity).apply {
                    text = label
                    textSize = 12f
                    setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
                    gravity = Gravity.CENTER
                    setTextColor(if (active) coral else secondary)
                })
            }
        }
        nav.addView(item("Home", "⌂", "home") { renderHome() }, LinearLayout.LayoutParams(0, -1, 1f))
        nav.addView(item("History", "▤", "history") { renderHistory() }, LinearLayout.LayoutParams(0, -1, 1f))
        return nav
    }

    private fun pageColumn() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(12), dp(24), dp(20))
    }

    private fun brandHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(19))
        }
        val mark = TextView(this).apply {
            text = "C"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = rounded(coral, 14)
        }
        row.addView(mark, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(10) })
        row.addView(TextView(this).apply {
            text = "CrediVoice"
            textSize = 21f
            setTextColor(ink)
            setTypeface(null, Typeface.BOLD)
        })
        return row
    }

    private fun heading(titleText: String, subtitle: String): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(20))
        }
        box.addView(TextView(this).apply {
            text = titleText
            textSize = 30f
            setTextColor(ink)
            setTypeface(null, Typeface.BOLD)
            setLineSpacing(0f, 1.02f)
        })
        box.addView(TextView(this).apply {
            text = subtitle
            textSize = 15f
            setTextColor(secondary)
            setPadding(0, dp(7), 0, 0)
        })
        return box
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(17), dp(18), dp(17))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), border)
        }
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 17f
        setTextColor(ink)
        setTypeface(null, Typeface.BOLD)
    }

    private fun body(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(secondary)
        setPadding(0, dp(4), 0, dp(2))
    }

    private fun horizontalRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun accentSwitch(
        checked: Boolean,
        checkedThumb: Int,
        checkedTrack: Int,
        action: (Boolean) -> Unit
    ) = Switch(this).apply {
        isChecked = checked
        showText = false
        if (Build.VERSION.SDK_INT >= 21) {
            thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(checkedThumb, Color.WHITE)
            )
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(checkedTrack, Color.rgb(205, 205, 205))
            )
        }
        setOnCheckedChangeListener { _, isOn -> action(isOn) }
    }

    private fun textAction(text: String, action: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(coral)
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
        minHeight = dp(44)
        setPadding(dp(10), dp(8), dp(10), dp(8))
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun filledAction(text: String, action: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(Color.WHITE)
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        minHeight = dp(54)
        background = rounded(coral, 18)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun sectionParams() = LinearLayout.LayoutParams(-1, -2).apply {
        bottomMargin = dp(12)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

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

    private fun speakTest() {
        speakWithPhoneVolume(
            "CrediVoice is ready. This is an Indian English voice sample.",
            "test-voice"
        )
    }

    private fun speakHistoryEvent(event: CreditEvent) {
        val spokenAmount = event.amount
        val message = if (spokenAmount.isBlank()) {
            "Credit alert from ${event.sender}."
        } else {
            "Credit alert. ${spokenAmount} credited by ${event.sender}."
        }
        speakWithPhoneVolume(message, "history-${event.time}")
    }

    private fun speakWithPhoneVolume(text: String, utteranceId: String) {
        pendingActivitySpeech = text to utteranceId
        if (testTts == null) {
            testTts = TextToSpeech(this) { status -> onActivityTtsInitialized(status) }
        } else if (activityTtsInitialized) {
            if (!activityTtsReady) {
                if (configureIndianEnglish(testTts)) {
                    activityTtsReady = true
                } else {
                    pendingActivitySpeech = null
                    showIndianEnglishUnavailable()
                    return
                }
            }
            speakPendingActivitySpeech()
        }
    }

    private fun onActivityTtsInitialized(status: Int) {
        activityTtsInitialized = true
        if (status != TextToSpeech.SUCCESS) {
            pendingActivitySpeech = null
            Toast.makeText(this, "Check your device text-to-speech settings.", Toast.LENGTH_LONG).show()
            return
        }
        if (!configureIndianEnglish(testTts)) {
            pendingActivitySpeech = null
            showIndianEnglishUnavailable()
            return
        }
        activityTtsReady = true
        speakPendingActivitySpeech()
    }

    private fun showIndianEnglishUnavailable() {
        AlertDialog.Builder(this)
            .setTitle("English (India) voice unavailable")
            .setMessage("Install the English (India) voice in your device text-to-speech settings, then try again.")
            .setNegativeButton("Close", null)
            .setPositiveButton("Voice settings") { _, _ ->
                try {
                    startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                } catch (_: Exception) {
                    Toast.makeText(this, "Open Settings and search for Text-to-speech.", Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    private fun configureIndianEnglish(engine: TextToSpeech?): Boolean {
        if (engine == null) return false
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        val languageResult = engine.setLanguage(java.util.Locale("en", "IN"))
        return languageResult >= TextToSpeech.LANG_AVAILABLE
    }

    private fun speakPendingActivitySpeech() {
        if (!activityTtsReady) return
        val pending = pendingActivitySpeech ?: return
        pendingActivitySpeech = null
        testTts?.speak(pending.first, TextToSpeech.QUEUE_FLUSH, null, pending.second)
    }

    override fun onDestroy() {
        testTts?.stop()
        testTts?.shutdown()
        testTts = null
        super.onDestroy()
    }

    private fun notificationAccessGranted(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        val component = ComponentName(this, CreditNotificationListener::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    private fun openNotificationAccessSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, "Open Android Settings and search for Notification access.", Toast.LENGTH_LONG).show()
        }
    }

    private fun formatMinutes(value: Int): String {
        val hour = value / 60
        val minute = value % 60
        val suffix = if (hour < 12) "AM" else "PM"
        val displayHour = when (hour % 12) { 0 -> 12; else -> hour % 12 }
        return "%d:%02d %s".format(displayHour, minute, suffix)
    }
}
