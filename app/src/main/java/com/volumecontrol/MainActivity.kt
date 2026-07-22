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
    private var vizPoll: Runnable? = null

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
        loadScaleDisplay()
        updateVisualizerRange()
        startVizPoll()
    }

    override fun onPause() {
        super.onPause()
        stopVizPoll()
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
        btnRetry.setOnClickListener { checkPermissions() }

        seekMinDb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                val db = progress - 96
                tvMinDb.text = getString(R.string.db_label, db)
                if (progress > seekMaxDb.progress) seekMaxDb.progress = progress
                updateAvgDisplay()
                updateVisualizerRange()
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) { sendEqualizerUpdate() }
        })

        seekMaxDb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                val db = progress - 96
                tvMaxDb.text = getString(R.string.db_label, db)
                if (progress < seekMinDb.progress) seekMinDb.progress = progress
                updateAvgDisplay()
                updateVisualizerRange()
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) { sendEqualizerUpdate() }
        })

        switchService.setOnCheckedChangeListener { _, isChecked ->
            if (!checkPermissionsForService()) {
                switchService.isChecked = false
                return@setOnCheckedChangeListener
            }
            if (isChecked) startService() else stopService()
        }

        seekVolumeScale.max = 450
        seekVolumeScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                tvScaleStatus.text = getString(R.string.scale_status, progress + 50)
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) { sendScaleUpdate(s.progress + 50) }
        })

        btnConstantTone.setOnClickListener {
            if (testTonePlayer.isPlaying()) {
                testTonePlayer.stop(); resetToneButtons(); releaseVisualizer()
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
                testTonePlayer.stop(); resetToneButtons(); releaseVisualizer()
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

    private fun progressToDb(progress: Int) = progress - 96
    private fun dbToProgress(db: Int) = (db + 96).coerceIn(0, 96)

    private fun updateAvgDisplay() {
        val avgDb = (progressToDb(seekMinDb.progress) + progressToDb(seekMaxDb.progress)) / 2
        tvAvgDb.text = getString(R.string.avg_label, avgDb)
    }

    private fun updateVisualizerRange() {
        visualizerView.setRange(
            progressToDb(seekMinDb.progress).toFloat(),
            progressToDb(seekMaxDb.progress).toFloat()
        )
    }

    private fun startVizForTestTone() {
        releaseVisualizer()
        val sid = testTonePlayer.getAudioSessionId()
        if (sid <= 0) { vizHandler.postDelayed({ startVizForTestTone() }, 150); return }
        try {
            viz = Visualizer(sid).apply {
                setCaptureSize(Visualizer.getCaptureSizeRange()[1])
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer, waveform: ByteArray, sr: Int) {
                        visualizerView.pushWaveform(waveform)
                    }
                    override fun onFftDataCapture(v: Visualizer, fft: ByteArray, sr: Int) {}
                }, Visualizer.getMaxCaptureRate() / 2, true, false)
                enabled = true
            }
        } catch (_: Exception) { viz = null }
    }

    private fun startVizPoll() {
        stopVizPoll()
        vizPoll = object : Runnable {
            override fun run() {
                val sid = testTonePlayer.getAudioSessionId()
                if (sid > 0 && viz == null) startVizForTestTone()
                if (sid <= 0 && viz != null) releaseVisualizer()
                vizHandler.postDelayed(this, 1500)
            }
        }
        vizHandler.post(vizPoll!!)
    }

    private fun stopVizPoll() { vizPoll?.let { vizHandler.removeCallbacks(it) }; vizPoll = null }
    private fun releaseVisualizer() { viz?.let { try { it.enabled = false; it.release() } catch (_: Exception) {} }; viz = null }

    private fun checkPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            showPermissionScreen()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return false
        }
        showControlsScreen()
        return true
    }

    private fun checkPermissionsForService(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            showPermissionScreen()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
        seekMinDb.progress = dbToProgress(prefs.getInt("min_db", -40))
        seekMaxDb.progress = dbToProgress(prefs.getInt("max_db", -10))
        tvMinDb.text = getString(R.string.db_label, progressToDb(seekMinDb.progress))
        tvMaxDb.text = getString(R.string.db_label, progressToDb(seekMaxDb.progress))
        updateAvgDisplay()
        val scale = prefs.getInt("volume_scale", 100)
        seekVolumeScale.progress = scale - 50
        tvScaleStatus.text = getString(R.string.scale_status, scale)
        switchService.isChecked = AudioProcessingService.isRunning
        updateVisualizerRange()
    }

    private fun loadScaleDisplay() {
        val prefs = getSharedPreferences("volume_control", MODE_PRIVATE)
        val scale = prefs.getInt("volume_scale", 100)
        tvScaleStatus.text = getString(R.string.scale_status, scale)
        seekVolumeScale.progress = scale - 50
    }

    private fun sendEqualizerUpdate() {
        val minDb = progressToDb(seekMinDb.progress)
        val maxDb = progressToDb(seekMaxDb.progress)
        getSharedPreferences("volume_control", MODE_PRIVATE).edit()
            .putInt("min_db", minDb).putInt("max_db", maxDb).apply()
        startService(Intent(this, AudioProcessingService::class.java).apply {
            action = "UPDATE_EQUALIZER"
            putExtra("min_db", minDb)
            putExtra("max_db", maxDb)
        })
    }

    private fun sendScaleUpdate(scale: Int) {
        getSharedPreferences("volume_control", MODE_PRIVATE).edit()
            .putInt("volume_scale", scale).apply()
        startService(Intent(this, AudioProcessingService::class.java).apply {
            action = "SET_SCALE"
            putExtra("scale", scale)
        })
    }

    private fun startService() {
        val intent = Intent(this, AudioProcessingService::class.java).apply { action = "START" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun stopService() {
        startService(Intent(this, AudioProcessingService::class.java).apply { action = "STOP" })
        switchService.isChecked = false
    }
}
