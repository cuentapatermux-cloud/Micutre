// Copyright 2026 PollNull

package com.miclite.voz

import android.animation.ValueAnimator
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.net.Uri
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.Locale
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.provider.OpenableColumns
import java.util.WeakHashMap
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class MainActivity : Activity() {
    private data class SoundboardSound(
        val id: String,
        var name: String,
        val uri: String,
        var isPinned: Boolean = false,
        var isLooping: Boolean = false,
        var isVisible: Boolean = true
    )

    private val audioEngine = AudioEngine()
    private val interfaceTypeface by lazy(LazyThreadSafetyMode.NONE) {
        Typeface.createFromAsset(assets, "fonts/SpaceGrotesk-Regular.ttf")
    }
    private val interfaceFontBaseStyles = WeakHashMap<TextView, Int>()
    private var isLive = false
    private lateinit var mainButton: MaterialButton
    private lateinit var pager: ViewPager2
    private lateinit var buttonHalo: View
    private lateinit var buttonWaves: List<View>
    private val buttonWaveAnimators = mutableListOf<ValueAnimator>()
    private lateinit var stateLabel: TextView
    private lateinit var routeLabel: TextView
    private lateinit var bluetoothStatusLabel: TextView
    private lateinit var bluetoothIndicator: View
    private lateinit var volumeValue: TextView
    private lateinit var effectsRow: LinearLayout
    private lateinit var soundboardGrid: LinearLayout
    private lateinit var soundboardStatus: TextView
    private lateinit var audioManager: AudioManager
    private var audioCallbackRegistered = false
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refreshBluetoothStatus()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refreshBluetoothStatus()
    }
    private var gain = 0.8f
    private var soundPlayer: MediaPlayer? = null
    private var currentSoundId: String? = null
    private val importedSounds = mutableListOf<SoundboardSound>()
    private lateinit var builtInEffects: List<VoiceEffect>
    private val customEffects = mutableListOf<VoiceEffect>()
    private lateinit var selectedEffect: VoiceEffect
    private var surface = Color.BLACK
    private var onSurface = Color.WHITE
    private var surfaceVariant = Color.DKGRAY
    private var onSurfaceVariant = Color.LTGRAY
    private var primary = Color.CYAN
    private var onPrimary = Color.BLACK
    private var primaryContainer = Color.BLUE
    private var onPrimaryContainer = Color.WHITE

    override fun attachBaseContext(newBase: Context) {
        val language = newBase.getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(KEY_LANGUAGE, LANGUAGE_SPANISH) ?: LANGUAGE_SPANISH
        val locale = when (language) {
            LANGUAGE_ENGLISH -> Locale.ENGLISH
            LANGUAGE_CHINESE -> Locale.SIMPLIFIED_CHINESE
            else -> Locale("es")
        }
        val configuration = Configuration(newBase.resources.configuration)
        configuration.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        builtInEffects = listOf(
            VoiceEffect(getString(R.string.effect_natural)),
            VoiceEffect(getString(R.string.effect_high), pitchSemitones = 5f),
            VoiceEffect(getString(R.string.effect_echo), echoMix = 0.42f, echoDelayMs = 210f),
            VoiceEffect(getString(R.string.effect_low), pitchSemitones = -5f),
            VoiceEffect(getString(R.string.effect_robotic), robotMix = 1f)
        )
        selectedEffect = builtInEffects.first()
        loadSavedEffects()
        loadSavedSounds()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        surface = color(com.google.android.material.R.attr.colorSurface, Color.BLACK)
        onSurface = color(com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
        surfaceVariant = color(com.google.android.material.R.attr.colorSurfaceVariant, Color.DKGRAY)
        onSurfaceVariant = color(com.google.android.material.R.attr.colorOnSurfaceVariant, Color.LTGRAY)
        primary = color(androidx.appcompat.R.attr.colorPrimary, Color.CYAN)
        onPrimary = color(com.google.android.material.R.attr.colorOnPrimary, Color.BLACK)
        primaryContainer = color(com.google.android.material.R.attr.colorPrimaryContainer, Color.BLUE)
        onPrimaryContainer = color(com.google.android.material.R.attr.colorOnPrimaryContainer, Color.WHITE)

        window.statusBarColor = surface
        window.navigationBarColor = surface
        val isNight = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        window.decorView.systemUiVisibility = if (isNight) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        val screen = makePagerScreen()
        setContentView(screen)
        ViewCompat.requestApplyInsets(screen)
    }

    override fun onResume() {
        super.onResume()
        if (!audioCallbackRegistered) {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
            audioCallbackRegistered = true
        }
        refreshBluetoothStatus()
    }

    override fun onPause() {
        if (audioCallbackRegistered) {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
            audioCallbackRegistered = false
        }
        super.onPause()
    }

    private fun makePagerScreen(): View = ViewPager2(this).apply {
        pager = this
        orientation = ViewPager2.ORIENTATION_HORIZONTAL
        offscreenPageLimit = 1
        setPageTransformer { page, position ->
            if (reduceMotionEnabled()) {
                page.alpha = 1f
                page.scaleX = 1f
                page.scaleY = 1f
            } else {
                val progress = (1f - abs(position)).coerceIn(0f, 1f)
                val easedProgress = progress * progress * (3f - 2f * progress)
                val scale = 0.97f + 0.03f * easedProgress
                page.alpha = 0.76f + 0.24f * easedProgress
                page.scaleX = scale
                page.scaleY = scale
            }
        }
        adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = 3
            override fun getItemViewType(position: Int) = position
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val page = when (viewType) {
                    0 -> makeMicrophonePage()
                    1 -> makeSoundboardPage()
                    else -> makeSettingsPage()
                }
                applyInterfaceFont(page)
                page.layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.MATCH_PARENT
                )
                return object : RecyclerView.ViewHolder(page) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
        }
    }

    private fun makeMicrophonePage(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(26), dp(24), dp(22))
            setBackgroundColor(surface)
            clipChildren = false
            clipToPadding = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(page) { view, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                maxOf(dp(24), safe.left + dp(16)),
                maxOf(dp(26), safe.top + dp(12)),
                maxOf(dp(24), safe.right + dp(16)),
                maxOf(dp(22), safe.bottom + dp(12))
            )
            insets
        }

        val eyebrow = TextView(this).apply {
            text = getString(R.string.mic_header)
            textSize = 12f
            letterSpacing = .11f
            setTextColor(primary)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE
        }
        page.addView(eyebrow, centeredParams(bottom = 9))

        val effectsHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        effectsHeader.addView(TextView(this).apply {
            text = getString(R.string.effects_header)
            textSize = 13f
            letterSpacing = .08f
            setTextColor(onSurface)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        effectsHeader.addView(MaterialButton(this).apply {
            text = getString(R.string.import_effect)
            isAllCaps = false
            textSize = 12f
            insetTop = 0
            insetBottom = 0
            minHeight = dp(40)
            setOnClickListener { launchImportEffect() }
        })
        page.addView(effectsHeader, centeredParams(bottom = 7))

        effectsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        renderEffectButtons()
        val effectsScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(effectsRow)
        }
        page.addView(effectsScroller, centeredParams(bottom = 10).apply { height = dp(52) })

        val playArea = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        buttonHalo = View(this).apply {
            alpha = 0f
            background = rounded(primaryContainer, dp(120))
            elevation = dp(8).toFloat()
        }
        playArea.addView(buttonHalo, FrameLayout.LayoutParams(dp(226), dp(226), Gravity.CENTER))

        buttonWaves = List(3) {
            View(this).apply {
                alpha = 0f
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(2), withAlpha(primary, 128))
                }
            }.also { wave ->
                playArea.addView(wave, FrameLayout.LayoutParams(dp(208), dp(208), Gravity.CENTER))
            }
        }

        mainButton = MaterialButton(this).apply {
            setText("")
            setIconResource(R.drawable.ic_play)
            iconTint = ColorStateList.valueOf(onPrimary)
            iconSize = dp(72)
            iconPadding = 0
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            backgroundTintList = ColorStateList.valueOf(primary)
            rippleColor = ColorStateList.valueOf(primaryContainer)
            cornerRadius = dp(104)
            insetTop = 0
            insetBottom = 0
            insetLeft = 0
            insetRight = 0
            elevation = dp(7).toFloat()
            contentDescription = getString(R.string.microphone_button_start)
            isFocusable = true
            setOnClickListener {
                animateButtonPress()
                onMicPressed()
            }
        }
        playArea.addView(mainButton, FrameLayout.LayoutParams(dp(208), dp(208), Gravity.CENTER))
        page.addView(playArea, centeredParams(bottom = 5).apply { height = dp(320) })

        stateLabel = TextView(this).apply {
            text = getString(R.string.mic_off)
            textSize = 12f
            letterSpacing = .15f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(onSurfaceVariant)
            gravity = Gravity.CENTER
        }
        page.addView(stateLabel, centeredParams(bottom = 14))

        val routeCard = MaterialCardView(this).apply {
            radius = dp(24).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = surfaceVariant
            setCardBackgroundColor(surfaceVariant)
            useCompatPadding = false
        }
        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(15), dp(17), dp(12))
        }
        val routeTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val routeText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bluetoothIndicator = View(this).apply {
            background = rounded(onSurfaceVariant, dp(10))
        }
        routeTop.addView(bluetoothIndicator, LinearLayout.LayoutParams(dp(10), dp(10)).apply {
            marginEnd = dp(12)
        })
        bluetoothStatusLabel = TextView(this).apply {
            text = getString(R.string.bluetooth_disconnected)
            textSize = 10f
            letterSpacing = .08f
            setTextColor(onSurfaceVariant)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        routeText.addView(bluetoothStatusLabel)
        routeLabel = TextView(this).apply {
            text = getString(R.string.no_device_connected)
            textSize = 15f
            setTextColor(onSurface)
            setPadding(0, dp(3), 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        routeText.addView(routeLabel)
        routeTop.addView(routeText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        cardContent.addView(routeTop)

        val volumeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(13), 0, 0)
        }
        val volume = Slider(this).apply {
            valueFrom = 0f
            valueTo = 100f
            stepSize = 1f
            value = 80f
            trackHeight = dp(9)
            thumbRadius = dp(10)
            haloRadius = dp(21)
            trackActiveTintList = ColorStateList.valueOf(primary)
            trackInactiveTintList = ColorStateList.valueOf(withAlpha(onSurfaceVariant, 72))
            thumbTintList = ColorStateList.valueOf(primary)
            haloTintList = ColorStateList.valueOf(withAlpha(primaryContainer, 110))
            addOnChangeListener { _, sliderValue, _ ->
                gain = sliderValue / 100f
                audioEngine.setGain(gain)
                volumeValue.text = getString(R.string.volume_percent, sliderValue.toInt())
            }
        }
        volumeRow.addView(volume, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
            marginEnd = dp(10)
        })
        volumeValue = TextView(this).apply {
            text = getString(R.string.volume_percent, 80)
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(primary)
            gravity = Gravity.END
        }
        volumeRow.addView(volumeValue, LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT))
        volume.setLabelFormatter { "${it.toInt()}%" }
        cardContent.addView(volumeRow)
        routeCard.addView(cardContent)
        page.addView(routeCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val note = TextView(this).apply {
            text = getString(R.string.microphone_note)
            textSize = 12f
            setTextColor(onSurfaceVariant)
            gravity = Gravity.CENTER
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        page.addView(note, centeredParams(top = 14))
        page.addView(TextView(this).apply {
            text = getString(R.string.soundboard_hint)
            textSize = 11f
            letterSpacing = .04f
            setTextColor(primary)
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE
        }, centeredParams(top = 12))
        page.addView(settingsShortcut(), centeredParams(top = 8))
        return ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(page)
        }
    }

    private fun makeSoundboardPage(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(26), dp(24), dp(22))
            setBackgroundColor(surface)
            clipChildren = false
            clipToPadding = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(page) { view, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                maxOf(dp(24), safe.left + dp(16)),
                maxOf(dp(26), safe.top + dp(12)),
                maxOf(dp(24), safe.right + dp(16)),
                maxOf(dp(22), safe.bottom + dp(12))
            )
            insets
        }

        page.addView(TextView(this).apply {
            text = getString(R.string.microphone_page_label)
            textSize = 12f
            letterSpacing = .08f
            setTextColor(primary)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE
        }, centeredParams(bottom = 14))

        page.addView(TextView(this).apply {
            text = getString(R.string.soundboard_title)
            textSize = 30f
            setTextColor(onSurface)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
        }, centeredParams(bottom = 5))
        page.addView(TextView(this).apply {
            text = getString(R.string.soundboard_instructions)
            textSize = 14f
            setTextColor(onSurfaceVariant)
            gravity = Gravity.CENTER
        }, centeredParams(bottom = 18))

        val importButton = MaterialButton(this).apply {
            text = getString(R.string.import_audio)
            isAllCaps = false
            setOnClickListener { showAudioImportFormats() }
        }
        page.addView(importButton, centeredParams(bottom = 5))
        page.addView(TextView(this).apply {
            text = getString(R.string.audio_formats_short)
            textSize = 11f
            setTextColor(onSurfaceVariant)
            gravity = Gravity.CENTER
            setLineSpacing(dp(2).toFloat(), 1f)
        }, centeredParams(bottom = 16))

        val playbackRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        soundboardStatus = TextView(this).apply {
            text = getString(R.string.ready)
            textSize = 13f
            setTextColor(onSurfaceVariant)
        }
        playbackRow.addView(soundboardStatus, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        playbackRow.addView(MaterialButton(this).apply {
            text = getString(R.string.stop)
            isAllCaps = false
            textSize = 12f
            minHeight = dp(40)
            insetTop = 0
            insetBottom = 0
            setOnClickListener { stopSound() }
        })
        page.addView(playbackRow, centeredParams(bottom = 8))

        soundboardGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        renderSoundboardTiles()
        page.addView(soundboardGrid, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        return ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(page)
        }
    }

    private fun settingsShortcut() = MaterialButton(this).apply {
        text = getString(R.string.settings_open)
        isAllCaps = false
        minHeight = dp(40)
        insetTop = 0
        insetBottom = 0
        setOnClickListener { pager.currentItem = 2 }
    }

    private fun makeSettingsPage(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(60), dp(24), dp(24))
            setBackgroundColor(surface)
        }
        ViewCompat.setOnApplyWindowInsetsListener(page) { view, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                maxOf(dp(24), safe.left + dp(16)),
                maxOf(dp(60), safe.top + dp(44)),
                maxOf(dp(24), safe.right + dp(16)),
                maxOf(dp(24), safe.bottom + dp(12))
            )
            insets
        }

        page.addView(TextView(this).apply {
            text = getString(R.string.settings_title)
            textSize = 30f
            setTextColor(onSurface)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
        }, centeredParams(bottom = 4))
        page.addView(TextView(this).apply {
            text = getString(R.string.settings_subtitle)
            textSize = 14f
            setTextColor(onSurfaceVariant)
            gravity = Gravity.CENTER
        }, centeredParams(bottom = 20))

        page.addView(MaterialButton(this).apply {
            text = languageButtonLabel()
            isAllCaps = false
            setOnClickListener { showLanguagePicker() }
        }, centeredParams(bottom = 14))

        val reduceMotion = SwitchMaterial(this).apply {
            text = getString(R.string.settings_reduce_motion)
            isChecked = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_REDUCE_MOTION, false)
            setOnCheckedChangeListener { _, checked ->
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(KEY_REDUCE_MOTION, checked).apply()
                if (checked) stopButtonWaves() else if (isLive) startButtonWaves()
            }
        }
        page.addView(reduceMotion, centeredParams(bottom = 2))
        page.addView(TextView(this).apply {
            text = getString(R.string.settings_reduce_motion_summary)
            textSize = 13f
            setTextColor(onSurfaceVariant)
        }, centeredParams(bottom = 14))

        val boldText = SwitchMaterial(this).apply {
            text = getString(R.string.settings_bold_text)
            isChecked = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_BOLD_TEXT, false)
            setOnCheckedChangeListener { _, checked ->
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(KEY_BOLD_TEXT, checked).apply()
                applyInterfaceFont(pager, checked)
            }
        }
        page.addView(boldText, centeredParams(bottom = 2))
        page.addView(TextView(this).apply {
            text = getString(R.string.settings_bold_text_summary)
            textSize = 13f
            setTextColor(onSurfaceVariant)
        }, centeredParams(bottom = 14))

        val keepScreenOn = SwitchMaterial(this).apply {
            text = getString(R.string.settings_keep_screen_on)
            isChecked = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_KEEP_SCREEN_ON, true)
            setOnCheckedChangeListener { _, checked ->
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(KEY_KEEP_SCREEN_ON, checked).apply()
                if (isLive) {
                    if (checked) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
        page.addView(keepScreenOn, centeredParams(bottom = 2))
        page.addView(TextView(this).apply {
            text = getString(R.string.settings_keep_screen_on_summary)
            textSize = 13f
            setTextColor(onSurfaceVariant)
        }, centeredParams(bottom = 22))

        page.addView(TextView(this).apply {
            text = getString(R.string.settings_about)
            textSize = 12f
            letterSpacing = .08f
            setTextColor(onSurfaceVariant)
            gravity = Gravity.CENTER
        }, centeredParams(bottom = 4))
        page.addView(TextView(this).apply {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
            text = getString(R.string.settings_version, versionName)
            textSize = 14f
            setTextColor(onSurface)
            gravity = Gravity.CENTER
        }, centeredParams(bottom = 18))
        page.addView(MaterialButton(this).apply {
            text = getString(R.string.back_to_microphone)
            isAllCaps = false
            setOnClickListener { pager.currentItem = 0 }
        }, centeredParams())

        return ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(page)
        }
    }

    private fun showLanguagePicker() {
        val languages = arrayOf(LANGUAGE_SPANISH, LANGUAGE_ENGLISH, LANGUAGE_CHINESE)
        val selected = languages.indexOf(
            getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LANGUAGE, LANGUAGE_SPANISH)
        ).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_language_dialog)
            .setSingleChoiceItems(R.array.language_names, selected) { dialog, which ->
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(KEY_LANGUAGE, languages[which]).apply()
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(R.string.cancel, null)
            .showWithInterfaceFont()
    }

    private fun languageButtonLabel(): String {
        val language = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(KEY_LANGUAGE, LANGUAGE_SPANISH)
        val languageName = when (language) {
            LANGUAGE_ENGLISH -> getString(R.string.language_english)
            LANGUAGE_CHINESE -> getString(R.string.language_chinese)
            else -> getString(R.string.language_spanish)
        }
        return "${getString(R.string.settings_language)}: $languageName"
    }

    private fun reduceMotionEnabled() = getSharedPreferences(PREFS, MODE_PRIVATE)
        .getBoolean(KEY_REDUCE_MOTION, false)

    private fun applyInterfaceFont(
        view: View,
        forceBold: Boolean = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_BOLD_TEXT, false)
    ) {
        if (view is TextView) {
            val baseStyle = interfaceFontBaseStyles.getOrPut(view) {
                if (view.typeface?.isBold == true) Typeface.BOLD else Typeface.NORMAL
            }
            val style = if (forceBold) Typeface.BOLD else baseStyle
            view.typeface = Typeface.create(interfaceTypeface, style)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) applyInterfaceFont(view.getChildAt(index), forceBold)
        }
    }

    private fun MaterialAlertDialogBuilder.showWithInterfaceFont() = show().also { dialog ->
        dialog.window?.decorView?.let(::applyInterfaceFont)
    }

    private fun animateButtonPress() {
        mainButton.animate().cancel()
        buttonHalo.animate().cancel()
        if (reduceMotionEnabled()) {
            mainButton.scaleX = 1f
            mainButton.scaleY = 1f
            buttonHalo.alpha = 0f
            buttonHalo.scaleX = 1f
            buttonHalo.scaleY = 1f
            return
        }
        mainButton.scaleX = .95f
        mainButton.scaleY = .95f
        mainButton.animate()
            .scaleX(1.035f)
            .scaleY(1.035f)
            .setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                mainButton.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .setInterpolator(OvershootInterpolator(1.25f))
                    .start()
            }
            .start()

        buttonHalo.animate().cancel()
        buttonHalo.scaleX = .84f
        buttonHalo.scaleY = .84f
        buttonHalo.alpha = .15f
        buttonHalo.animate()
            .scaleX(1.15f)
            .scaleY(1.15f)
            .alpha(.48f)
            .setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                buttonHalo.animate().alpha(0f).scaleX(1.3f).scaleY(1.3f).setDuration(240).start()
            }
            .start()
    }

    private fun startButtonWaves() {
        stopButtonWaves()
        if (reduceMotionEnabled()) return
        buttonWaves.forEachIndexed { index, wave ->
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1900L
                startDelay = index * 630L
                repeatCount = ValueAnimator.INFINITE
                interpolator = DecelerateInterpolator()
                addUpdateListener { animation ->
                    val progress = animation.animatedValue as Float
                    val scale = 1f + progress * .34f
                    wave.scaleX = scale
                    wave.scaleY = scale
                    wave.alpha = .42f * (1f - progress)
                }
            }
            buttonWaveAnimators += animator
            animator.start()
        }
    }

    private fun stopButtonWaves() {
        buttonWaveAnimators.forEach(ValueAnimator::cancel)
        buttonWaveAnimators.clear()
        if (::buttonWaves.isInitialized) {
            buttonWaves.forEach { wave ->
                wave.alpha = 0f
                wave.scaleX = 1f
                wave.scaleY = 1f
            }
        }
    }

    private fun animateSoundboardPress(view: View) {
        view.animate().cancel()
        if (reduceMotionEnabled()) {
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        view.scaleX = .95f
        view.scaleY = .95f
        view.animate()
            .scaleX(1.035f)
            .scaleY(1.035f)
            .setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .setInterpolator(OvershootInterpolator(1.25f))
                    .start()
            }
            .start()
    }

    private fun renderSoundboardTiles() {
        soundboardGrid.removeAllViews()
        val sounds = importedSounds.sortedByDescending { it.isPinned }
        sounds.chunked(2).forEachIndexed { rowIndex, rowSounds ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
            }
            rowSounds.forEachIndexed { columnIndex, sound ->
                val card = MaterialCardView(this).apply {
                    radius = dp(22).toFloat()
                    cardElevation = dp(1).toFloat()
                    strokeWidth = dp(2)
                    strokeColor = primary
                    setCardBackgroundColor(withAlpha(
                        if ((rowIndex * 2 + columnIndex) % 2 == 0) primaryContainer else surfaceVariant,
                        210
                    ))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        animateSoundboardPress(this)
                        playSound(sound)
                    }
                    setOnLongClickListener {
                        showSoundOptions(this, sound)
                        true
                    }
                }
                val content = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(10), dp(12), dp(10), dp(12))
                }
                content.addView(TextView(this).apply {
                    text = "♪"
                    textSize = 30f
                    gravity = Gravity.CENTER
                    setTextColor(onPrimaryContainer)
                })
                content.addView(TextView(this).apply {
                    text = sound.name
                    textSize = 14f
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    setTextColor(onPrimaryContainer)
                    gravity = Gravity.CENTER
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dp(5), 0, 0)
                })
                card.addView(content)
                row.addView(card, LinearLayout.LayoutParams(0, dp(132), 1f).apply {
                    if (columnIndex == 0) marginEnd = dp(8) else marginStart = dp(8)
                })
            }
            if (rowSounds.size == 1) {
                row.addView(View(this), LinearLayout.LayoutParams(0, dp(132), 1f).apply { marginStart = dp(8) })
            }
            soundboardGrid.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(132)
            ).apply { bottomMargin = dp(10) })
        }
        applyInterfaceFont(soundboardGrid)
    }

    private fun showSoundOptions(anchor: View, sound: SoundboardSound) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, MENU_RENAME, 0, getString(R.string.rename_sound))
        popup.menu.add(0, MENU_PIN, 0, getString(R.string.pin_sound)).apply {
            isCheckable = true
            isChecked = sound.isPinned
        }
        popup.menu.add(0, MENU_DELETE, 0, getString(R.string.delete))
        popup.menu.add(0, MENU_LOOP, 0, getString(R.string.loop_sound)).apply {
            isCheckable = true
            isChecked = sound.isLooping
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_RENAME -> {
                    showRenameSoundDialog(sound)
                    true
                }
                MENU_PIN -> {
                    sound.isPinned = !sound.isPinned
                    saveSounds()
                    renderSoundboardTiles()
                    true
                }
                MENU_DELETE -> {
                    confirmDeleteSound(sound)
                    true
                }
                MENU_LOOP -> {
                    sound.isLooping = !sound.isLooping
                    if (currentSoundId == sound.id && soundPlayer?.isPlaying == true) {
                        soundPlayer?.isLooping = sound.isLooping
                    }
                    saveSounds()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showRenameSoundDialog(sound: SoundboardSound) {
        val nameInput = EditText(this).apply {
            setSingleLine(true)
            setText(sound.name)
            setSelection(text.length)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename_sound)
            .setView(nameInput)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = nameInput.text.toString().trim().take(40)
                if (newName.isNotEmpty()) {
                    sound.name = newName
                    saveSounds()
                    renderSoundboardTiles()
                } else {
                    Toast.makeText(this, R.string.empty_sound_name, Toast.LENGTH_SHORT).show()
                }
            }
            .showWithInterfaceFont()
    }

    private fun confirmDeleteSound(sound: SoundboardSound) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_sound)
            .setMessage(getString(R.string.confirm_delete_sound, sound.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                if (currentSoundId == sound.id) stopSound()
                importedSounds.removeAll { it.id == sound.id }
                saveSounds()
                renderSoundboardTiles()
            }
            .showWithInterfaceFont()
    }

    private fun showAudioImportFormats() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.audio_formats_title)
            .setMessage(R.string.audio_formats_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.choose_files) { _, _ -> launchAudioPicker() }
            .showWithInterfaceFont()
    }

    private fun launchAudioPicker() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
            putExtra(android.content.Intent.EXTRA_TITLE, getString(R.string.audio_picker_title))
        }
        startActivityForResult(intent, REQUEST_IMPORT_AUDIO)
    }

    private fun importAudioSelection(data: android.content.Intent?) {
        if (data == null) return
        val uris = mutableListOf<Uri>()
        data.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) uris += clip.getItemAt(index).uri
        }
        data.data?.let { if (it !in uris) uris += it }
        if (uris.isEmpty()) return

        val readPermission = data.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        uris.forEach { uri ->
            if (readPermission != 0) {
                try {
                    contentResolver.takePersistableUriPermission(uri, readPermission)
                } catch (_: SecurityException) {
                    // Some document providers offer temporary rather than persistable access.
                }
            }
            importedSounds.removeAll { it.id == uri.toString() }
            importedSounds += SoundboardSound(uri.toString(), displayName(uri), uri.toString())
        }
        saveSounds()
        renderSoundboardTiles()
        Toast.makeText(
            this,
            resources.getQuantityString(R.plurals.audio_added, uris.size, uris.size),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun displayName(uri: Uri): String {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.substringBeforeLast('.')?.take(32)
                    ?.takeIf { it.isNotBlank() }
                else null
            } ?: getString(R.string.audio_imported_name)
        } catch (_: Exception) {
            getString(R.string.audio_imported_name)
        }
    }

    private fun playSound(sound: SoundboardSound) {
        stopSound(updateStatus = false)
        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                setDataSource(this@MainActivity, Uri.parse(sound.uri))
            }
            soundPlayer = player
            currentSoundId = sound.id
            player.setOnCompletionListener { completed ->
                if (soundPlayer === completed) {
                    soundPlayer = null
                    currentSoundId = null
                    completed.release()
                    if (::soundboardStatus.isInitialized) soundboardStatus.text = getString(R.string.ready)
                }
            }
            player.setOnErrorListener { failed, _, _ ->
                if (soundPlayer === failed) {
                    soundPlayer = null
                    currentSoundId = null
                }
                failed.release()
                if (::soundboardStatus.isInitialized) soundboardStatus.text = getString(R.string.audio_unavailable_format)
                Toast.makeText(this, R.string.android_audio_failed, Toast.LENGTH_LONG).show()
                true
            }
            soundboardStatus.text = getString(R.string.loading_sound, sound.name)
            player.setOnPreparedListener { ready ->
                if (soundPlayer === ready) {
                    soundboardStatus.text = getString(R.string.playing_sound, sound.name)
                    ready.isLooping = sound.isLooping
                    ready.start()
                } else ready.release()
            }
            player.prepareAsync()
        } catch (_: Exception) {
            stopSound(updateStatus = false)
            soundboardStatus.text = getString(R.string.audio_open_failed)
            Toast.makeText(this, getString(R.string.audio_open_failed_named, sound.name), Toast.LENGTH_LONG).show()
        }
    }

    private fun stopSound(updateStatus: Boolean = true) {
        val player = soundPlayer
        soundPlayer = null
        currentSoundId = null
        if (player != null) {
            try {
                player.stop()
            } catch (_: IllegalStateException) {
                // It may still be preparing.
            }
            player.release()
        }
        if (updateStatus && ::soundboardStatus.isInitialized) soundboardStatus.text = getString(R.string.ready)
    }

    private fun loadSavedSounds() {
        val preferences = getSharedPreferences(PREFS, MODE_PRIVATE)
        val saved = preferences.getString(KEY_SOUNDBOARD, null) ?: return
        try {
            val array = JSONArray(saved)
            for (index in 0 until array.length()) {
                val sound = array.getJSONObject(index)
                importedSounds += SoundboardSound(
                    id = sound.getString("uri"),
                name = sound.optString("name", getString(R.string.audio_imported_name)),
                    uri = sound.getString("uri"),
                    isPinned = sound.optBoolean("pinned", false),
                    isLooping = sound.optBoolean("looping", false)
                )
            }
        } catch (_: Exception) {
            importedSounds.clear()
        }
    }

    private fun saveSounds() {
        val array = JSONArray()
        importedSounds.forEach { sound ->
            array.put(JSONObject()
                .put("uri", sound.uri)
                .put("name", sound.name)
                .put("pinned", sound.isPinned)
                .put("looping", sound.isLooping))
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_SOUNDBOARD, array.toString())
            .apply()
    }

    private fun renderEffectButtons() {
        effectsRow.removeAllViews()
        (customEffects.filter { it.isPinned } + builtInEffects + customEffects.filterNot { it.isPinned }).forEach { effect ->
            val active = effect === selectedEffect
            val button = MaterialButton(this).apply {
                text = effect.name
                isAllCaps = false
                textSize = 13f
                minWidth = 0
                insetLeft = 0
                insetRight = 0
                insetTop = 0
                insetBottom = 0
                cornerRadius = dp(18)
                setPadding(dp(15), 0, dp(15), 0)
                backgroundTintList = ColorStateList.valueOf(if (active) primaryContainer else surfaceVariant)
                setTextColor(if (active) onPrimaryContainer else onSurface)
            contentDescription = getString(R.string.effect_content_description, effect.name)
                setOnClickListener {
                    selectedEffect = effect
                    if (isLive) audioEngine.setEffect(effect)
                    renderEffectButtons()
                }
                if (customEffects.any { it === effect }) {
                    setOnLongClickListener {
                        showEffectOptions(this, effect)
                        true
                    }
                }
            }
            effectsRow.addView(button, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)
            ).apply { marginEnd = dp(8) })
        }
        applyInterfaceFont(effectsRow)
    }

    private fun showEffectOptions(anchor: View, effect: VoiceEffect) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, MENU_RENAME, 0, getString(R.string.rename_effect))
        popup.menu.add(0, MENU_PIN, 0, getString(if (effect.isPinned) R.string.unpin_effect else R.string.pin_effect))
        popup.menu.add(0, MENU_DELETE, 0, getString(R.string.delete))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_RENAME -> {
                    showRenameEffectDialog(effect)
                    true
                }
                MENU_PIN -> {
                    effect.isPinned = !effect.isPinned
                    saveCustomEffects()
                    renderEffectButtons()
                    true
                }
                MENU_DELETE -> {
                    confirmDeleteEffect(effect)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showRenameEffectDialog(effect: VoiceEffect) {
        val nameInput = EditText(this).apply {
            setSingleLine(true)
            setText(effect.name)
            setSelection(text.length)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename_effect)
            .setView(nameInput)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = nameInput.text.toString().trim().take(24)
                if (newName.isNotEmpty()) {
                    val index = customEffects.indexOfFirst { it === effect }
                    if (index >= 0) {
                        val renamedEffect = effect.copy(name = newName)
                        customEffects[index] = renamedEffect
                        if (selectedEffect === effect) {
                            selectedEffect = renamedEffect
                            if (isLive) audioEngine.setEffect(renamedEffect)
                        }
                        saveCustomEffects()
                        renderEffectButtons()
                    }
                } else {
                    Toast.makeText(this, R.string.empty_effect_name, Toast.LENGTH_SHORT).show()
                }
            }
            .showWithInterfaceFont()
    }

    private fun confirmDeleteEffect(effect: VoiceEffect) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_effect)
            .setMessage(getString(R.string.confirm_delete_effect, effect.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                customEffects.removeAll { it === effect }
                if (selectedEffect === effect) {
                    selectedEffect = builtInEffects.first()
                    if (isLive) audioEngine.setEffect(selectedEffect)
                }
                saveCustomEffects()
                renderEffectButtons()
            }
            .showWithInterfaceFont()
    }

    private fun launchImportEffect() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(android.content.Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/*"))
        }
        startActivityForResult(intent, REQUEST_IMPORT_EFFECT)
    }

    @Deprecated("Uses the Android document picker for compatibility with the platform Activity base.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMPORT_EFFECT && resultCode == RESULT_OK) {
            data?.data?.let(::importCustomEffect)
        } else if (requestCode == REQUEST_IMPORT_AUDIO && resultCode == RESULT_OK) {
            importAudioSelection(data)
        }
    }

    private fun importCustomEffect(uri: Uri) {
        try {
            val source = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalArgumentException(getString(R.string.preset_read_failed))
            require(source.length <= 16_384) { getString(R.string.preset_too_large) }
            val json = JSONObject(source)
            require(json.has("pitchSemitones") || json.has("echoMix") || json.has("robotMix")) {
                getString(R.string.preset_missing_effects)
            }
            val defaultEffectName = getString(R.string.custom_effect_name)
            val name = json.optString("name", defaultEffectName).trim().take(24)
                .ifBlank { defaultEffectName }
            val effect = VoiceEffect(
                name = name,
                pitchSemitones = json.optDouble("pitchSemitones", 0.0).toFloat().coerceIn(-12f, 12f),
                echoMix = json.optDouble("echoMix", 0.0).toFloat().coerceIn(0f, 0.8f),
                echoDelayMs = json.optDouble("echoDelayMs", 180.0).toFloat().coerceIn(60f, 500f),
                robotMix = json.optDouble("robotMix", 0.0).toFloat().coerceIn(0f, 1f)
            )
            customEffects.removeAll { it.name.equals(effect.name, ignoreCase = true) }
            customEffects += effect
            selectedEffect = effect
            saveCustomEffects()
            renderEffectButtons()
            if (isLive) audioEngine.setEffect(effect)
            Toast.makeText(this, getString(R.string.effect_imported, effect.name), Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.effect_import_failed)
                .setMessage(error.message ?: getString(R.string.effect_import_fallback))
                .setPositiveButton(R.string.got_it, null)
                .showWithInterfaceFont()
        }
    }

    private fun loadSavedEffects() {
        val saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_CUSTOM_EFFECTS, null) ?: return
        try {
            val array = JSONArray(saved)
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                customEffects += VoiceEffect(
                    name = item.optString("name", getString(R.string.custom_effect_name)),
                    pitchSemitones = item.optDouble("pitchSemitones", 0.0).toFloat().coerceIn(-12f, 12f),
                    echoMix = item.optDouble("echoMix", 0.0).toFloat().coerceIn(0f, 0.8f),
                    echoDelayMs = item.optDouble("echoDelayMs", 180.0).toFloat().coerceIn(60f, 500f),
                    robotMix = item.optDouble("robotMix", 0.0).toFloat().coerceIn(0f, 1f),
                    isPinned = item.optBoolean("pinned", false)
                )
            }
        } catch (_: Exception) {
            customEffects.clear()
        }
    }

    private fun saveCustomEffects() {
        val array = JSONArray()
        customEffects.forEach { effect ->
            array.put(JSONObject()
                .put("name", effect.name)
                .put("pitchSemitones", effect.pitchSemitones)
                .put("echoMix", effect.echoMix)
                .put("echoDelayMs", effect.echoDelayMs)
                .put("robotMix", effect.robotMix)
                .put("pinned", effect.isPinned))
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_CUSTOM_EFFECTS, array.toString()).apply()
    }

    private fun onMicPressed() {
        if (isLive) {
            stopMicrophone()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
            return
        }
        startMicrophone()
    }

    private fun startMicrophone() {
        val bluetoothOutput = findBluetoothOutput()
        val started = audioEngine.start(gain, selectedEffect, bluetoothOutput) { error ->
            runOnUiThread {
                stopMicrophone()
                stateLabel.text = getString(error)
            }
        }
        if (!started) {
            stateLabel.text = getString(R.string.microphone_start_failed)
            return
        }
        isLive = true
        startButtonWaves()
        if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_KEEP_SCREEN_ON, true)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        mainButton.setIconResource(R.drawable.ic_pause)
        mainButton.iconTint = ColorStateList.valueOf(onPrimaryContainer)
        mainButton.backgroundTintList = ColorStateList.valueOf(primaryContainer)
        mainButton.rippleColor = ColorStateList.valueOf(primary)
        mainButton.contentDescription = getString(R.string.microphone_button_stop)
        stateLabel.text = getString(R.string.microphone_active)
        stateLabel.setTextColor(primary)
        refreshBluetoothStatus()
    }

    private fun stopMicrophone() {
        audioEngine.stop()
        isLive = false
        stopButtonWaves()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (::mainButton.isInitialized) {
            mainButton.setIconResource(R.drawable.ic_play)
            mainButton.iconTint = ColorStateList.valueOf(onPrimary)
            mainButton.backgroundTintList = ColorStateList.valueOf(primary)
            mainButton.rippleColor = ColorStateList.valueOf(primaryContainer)
            mainButton.contentDescription = getString(R.string.microphone_button_start)
            stateLabel.setTextColor(onSurfaceVariant)
        }
        if (::stateLabel.isInitialized) stateLabel.text = getString(R.string.mic_off)
        refreshBluetoothStatus()
    }

    private fun findBluetoothOutput(): AudioDeviceInfo? {
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (android.os.Build.VERSION.SDK_INT >= 31 &&
                    (device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER || device.type == AudioDeviceInfo.TYPE_BLE_HEADSET))
        }
    }

    private fun refreshBluetoothStatus() {
        if (!::routeLabel.isInitialized || !::bluetoothStatusLabel.isInitialized || !::bluetoothIndicator.isInitialized) return
        val device = findBluetoothOutput()
        val connected = device != null
        bluetoothStatusLabel.text = getString(
            if (connected) R.string.bluetooth_connected else R.string.bluetooth_disconnected
        )
        bluetoothStatusLabel.setTextColor(if (connected) primary else onSurfaceVariant)
        routeLabel.text = device?.productName?.toString()?.takeIf { it.isNotBlank() }
            ?: getString(if (connected) R.string.bluetooth_speaker else R.string.no_device_connected)
        bluetoothIndicator.background = rounded(if (connected) primary else onSurfaceVariant, dp(10))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startMicrophone()
        } else if (requestCode == REQUEST_MIC) {
            stateLabel.text = getString(R.string.microphone_permission_needed)
        }
    }

    override fun onDestroy() {
        stopSound()
        stopMicrophone()
        super.onDestroy()
    }

    private fun color(attribute: Int, fallback: Int) = MaterialColors.getColor(this, attribute, fallback)

    private fun centeredParams(bottom: Int = 0, top: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(bottom); topMargin = dp(top) }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_MIC = 41
        private const val REQUEST_IMPORT_EFFECT = 42
        private const val REQUEST_IMPORT_AUDIO = 43
        private const val PREFS = "micutre_preferences"
        private const val KEY_CUSTOM_EFFECTS = "custom_voice_effects"
        private const val KEY_SOUNDBOARD = "soundboard_files"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_REDUCE_MOTION = "reduce_motion"
        private const val KEY_BOLD_TEXT = "bold_text"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val MENU_RENAME = 1
        private const val MENU_PIN = 2
        private const val MENU_DELETE = 3
        private const val MENU_LOOP = 4
        private const val LANGUAGE_SPANISH = "es"
        private const val LANGUAGE_ENGLISH = "en"
        private const val LANGUAGE_CHINESE = "zh"
    }
}
