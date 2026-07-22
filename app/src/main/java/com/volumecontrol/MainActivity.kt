package com.volumecontrol

import android.Manifest
import android.content.Intent
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var layoutPermissions: LinearLayout
    private lateinit var layoutControls: LinearLayout
    private lateinit var btnNotificationSettings: Button
    private lateinit var btnRetry: Button
    private lateinit var seekMinDb: SeekBar
    private lateinit var seekMaxDb: SeekBar
    private lateinit var tvMinDb: TextView
    private lateinit var tvMaxDb: TextView
    private lateinit var tvAvgDb: TextView
    private lateinit var switchService: SwitchCompat
    private lateinit var seekVolumeScale: SeekBar
    private lateinit var tvScaleStatus: TextView
    private lateinit var btnConstantTone: Button
    private lateinit var btnSweepTone: Button
    private lateinit var visualizerView: VisualizerView

    private val testTonePlayer = TestTonePlayer()
    private var viz: Visualizer? = null
    private val vizHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var vizCheckRunnable: Runnable? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> checkPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        layoutPermissions = findViewById(R.id.layout_permissions)
        layoutControls = findViewById(R.id.layout_controls)
        btnNotificationSettings = findViewById(R.id.btn_notification_settings)
        btnRetry = findViewById(R.id.btn_retry)
        seekMinDb = findViewById(R.id.seek_min_db)
        seekMaxDb = findViewById(R.id.seek_max_db)
        tvMinDb = findViewById(R.id.tv_min_db)
        tvMaxDb = findViewById(R.id.tv_max_db)
        tvAvgDb = findViewById(R.id.tv_avg_db)
        switchService = findViewById(R.id.switch_service)
        seekVolumeScale = findViewById(R.id.seek_volume_scale)
        tvScaleStatus = findViewById(R.id.tv_scale_status)
        btnConstantTone = findViewById(R.id.btn_constant_tone)
        btnSweepTone = findViewById(R.id.btn_sweep_tone)
        visualizerView = findViewById(R.id.visualizer)

        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        if (AudioProcessingService.isRunning) {
            loadScaleDisplay()
        }
        updateVisualizerRange()
        startPollingViz()
    }

    override fun onPause() {
        super.onPause()
        stopPollingViz()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseVisualizer()
        testTonePlayer.stop()
    }

    private fun setupListeners() {
        btnNotificationSettings.setOnClickListener {
            PermissionManager.openAppNotificationSettings(this)
        }

        btnRetry.setOnClickListener {
            checkPermissions()
        }

        seekMinDb.max = 96
        seekMaxDb.max = 96

        seekMinDb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val db = -progress
                tvMinDb.text = getString(R.string.db_label, db)
                if (progress > seekMaxDb.progress) {
                    seekMaxDb.progress = progress
                }
                updateAvgDisplay()
                updateVisualizerRange()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                sendEqualizerUpdate()
            }
        })

        seekMaxDb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val db = -progress
                tvMaxDb.text = getString(R.string.db_label, db)
                if (progress < seekMinDb.progress) {
                    seekMinDb.progress = progress
                }
                updateAvgDisplay()
                updateVisualizerRange()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                sendEqualizerUpdate()
            }
        })

        switchService.setOnCheckedChangeListener { _, isChecked ->
            if (!checkPermissionsForService()) {
                switchService.isChecked = false
                return@setOnCheckedChangeListener
            }
            if (isChecked) {
                startService()
            } else {
                stopService()
            }
        }

        seekVolumeScale.max = 450
        seekVolumeScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val scale = progress + 50
                tvScaleStatus.text = getString(R.string.scale_status, scale)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val scale = seekBar.progress + 50
                sendScaleUpdate(scale)
            }
        })

        btnConstantTone.setOnClickListener {
            if (testTonePlayer.isPlaying()) {
                testTonePlayer.stop()
                resetToneButtons()
                releaseVisualizer()
            } else {
                testTonePlayer.playConstantTone()
                btnConstantTone.text = "Stop Tone"
                btnConstantTone.backgroundTintList = TestTonePlayer.ACTIVE_TINT
                btnSweepTone.text = "Sweep Test"
                btnSweepTone.backgroundTintList = null
                startVizForTestTone()
            }
        }

        btnSweepTone.setOnClickListener {
            if (testTonePlayer.isPlaying()) {
                testTonePlayer.stop()
                resetToneButtons()
                releaseVisualizer()
            } else {
                testTonePlayer.playSweepTone()
                btnSweepTone.text = "Stop Sweep"
                btnSweepTone.backgroundTintList = TestTonePlayer.ACTIVE_TINT
                btnConstantTone.text = "Constant Tone"
                btnConstantTone.backgroundTintList = null
                startVizForTestTone()
            }
        }
    }

    private fun resetToneButtons() {
        btnConstantTone.text = "Constant Tone"
        btnConstantTone.backgroundTintList = null
        btnSweepTone.text = "Sweep Test"
        btnSweepTone.backgroundTintList = null
    }

    private fun updateAvgDisplay() {
        val avgProgress = (seekMinDb.progress + seekMaxDb.progress) / 2
        tvAvgDb.text = getString(R.string.avg_label, -avgProgress)
    }

    private fun updateVisualizerRange() {
        visualizerView.setRange(
            -seekMinDb.progress.toFloat(),
            -seekMaxDb.progress.toFloat()
        )
    }

    private fun startVizForTestTone() {
        releaseVisualizer()
        val sessionId = testTonePlayer.getAudioSessionId()
        if (sessionId <= 0) {
            vizHandler.postDelayed({ startVizForTestTone() }, 200)
            return
        }
        try {
            viz = Visualizer(sessionId).apply {
                setCaptureSize(Visualizer.getCaptureSizeRange()[1])
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        v: Visualizer, waveform: ByteArray, samplingRate: Int
                    ) {
                        visualizerView.pushWaveform(waveform)
                    }
                    override fun onFftDataCapture(
                        v: Visualizer, fft: ByteArray, samplingRate: Int
                    ) {}
                }, Visualizer.getMaxCaptureRate() / 2, true, false)
                enabled = true
            }
        } catch (e: Exception) {
            viz = null
        }
    }

    private fun startPollingViz() {
        stopPollingViz()
        vizCheckRunnable = object : Runnable {
            override fun run() {
                val sid = testTonePlayer.getAudioSessionId()
                if (sid > 0 && viz == null) {
                    startVizForTestTone()
                }
                if (sid <= 0 && viz != null) {
                    releaseVisualizer()
                }
                vizHandler.postDelayed(this, 1500)
            }
        }
        vizHandler.post(vizCheckRunnable!!)
    }

    private fun stopPollingViz() {
        vizCheckRunnable?.let { vizHandler.removeCallbacks(it) }
        vizCheckRunnable = null
    }

    private fun releaseVisualizer() {
        viz?.let {
            try { it.enabled = false; it.release() } catch (_: Exception) {}
        }
        viz = null
    }

    private fun checkPermissions(): Boolean {
        val notificationGranted = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
            else -> true
        }

        if (!notificationGranted) {
            showPermissionScreen()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            return false
        }

        showControlsScreen()
        return true
    }

    private fun checkPermissionsForService(): Boolean {
        val notificationGranted = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
            else -> true
        }

        if (!notificationGranted) {
            showPermissionScreen()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            return false
        }
        return true
    }

    private fun showPermissionScreen() {
        layoutPermissions.visibility = View.VISIBLE
        layoutControls.visibility = View.GONE
        switchService.isChecked = false
    }

    private fun showControlsScreen() {
        layoutPermissions.visibility = View.GONE
        layoutControls.visibility = View.VISIBLE
        loadSettings()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("volume_control", MODE_PRIVATE)
        seekMinDb.progress = prefs.getInt("min_db", 40)
        seekMaxDb.progress = prefs.getInt("max_db", 10)
        tvMinDb.text = getString(R.string.db_label, -seekMinDb.progress)
        tvMaxDb.text = getString(R.string.db_label, -seekMaxDb.progress)
        updateAvgDisplay()

        val scale = prefs.getInt("volume_scale", 100)
        seekVolumeScale.progress = scale - 50
        tvScaleStatus.text = getString(R.string.scale_status, scale)

        switchService.isChecked = AudioProcessingService.isRunning
        updateVisualizerRange()
        loadScaleDisplay()
    }

    private fun loadScaleDisplay() {
        val prefs = getSharedPreferences("volume_control", MODE_PRIVATE)
        val scale = prefs.getInt("volume_scale", 100)
        tvScaleStatus.text = getString(R.string.scale_status, scale)
        seekVolumeScale.progress = scale - 50
    }

    private fun sendEqualizerUpdate() {
        val prefs = getSharedPreferences("volume_control", MODE_PRIVATE)
        prefs.edit()
            .putInt("min_db", seekMinDb.progress)
            .putInt("max_db", seekMaxDb.progress)
            .apply()

        val intent = Intent(this, AudioProcessingService::class.java).apply {
            action = "UPDATE_EQUALIZER"
            putExtra("min_db", -seekMinDb.progress)
            putExtra("max_db", -seekMaxDb.progress)
        }
        startService(intent)
    }

    private fun sendScaleUpdate(scale: Int) {
        val prefs = getSharedPreferences("volume_control", MODE_PRIVATE)
        prefs.edit().putInt("volume_scale", scale).apply()

        val intent = Intent(this, AudioProcessingService::class.java).apply {
            action = "SET_SCALE"
            putExtra("scale", scale)
        }
        startService(intent)
    }

    private fun startService() {
        val intent = Intent(this, AudioProcessingService::class.java).apply {
            action = "START"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopService() {
        val intent = Intent(this, AudioProcessingService::class.java).apply {
            action = "STOP"
        }
        startService(intent)
        switchService.isChecked = false
    }
}
