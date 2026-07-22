package com.volumecontrol

import android.Manifest
import android.content.Intent
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
    private lateinit var tvScaleStatus: TextView
    private lateinit var switchService: SwitchCompat
    private lateinit var btnConstantTone: Button
    private lateinit var btnSweepTone: Button

    private val testTonePlayer = TestTonePlayer()

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
        tvScaleStatus = findViewById(R.id.tv_scale_status)
        switchService = findViewById(R.id.switch_service)
        btnConstantTone = findViewById(R.id.btn_constant_tone)
        btnSweepTone = findViewById(R.id.btn_sweep_tone)

        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        if (AudioProcessingService.isRunning) {
            loadScaleDisplay()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
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

        btnConstantTone.setOnClickListener {
            if (testTonePlayer.isPlayingConstant()) {
                testTonePlayer.stop()
                btnConstantTone.text = "Play Constant Tone"
            } else {
                testTonePlayer.playConstantTone()
                btnConstantTone.text = "Stop Constant Tone"
            }
        }

        btnSweepTone.setOnClickListener {
            if (testTonePlayer.isPlayingConstant()) {
                testTonePlayer.stop()
                btnConstantTone.text = "Play Constant Tone"
            }
            btnSweepTone.text = "Playing Sweep\u2026"
            btnSweepTone.isEnabled = false
            testTonePlayer.playSweepTone()
            Thread {
                Thread.sleep((TestTonePlayer.SWEEP_DURATION_S * 1000).toLong() + 500)
                runOnUiThread {
                    btnSweepTone.text = "Play Sweep Test"
                    btnSweepTone.isEnabled = true
                }
            }.start()
        }
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
        switchService.isChecked = AudioProcessingService.isRunning
        loadScaleDisplay()
    }

    private fun loadScaleDisplay() {
        val prefs = getSharedPreferences("volume_control", MODE_PRIVATE)
        val scale = prefs.getInt("volume_scale", 100)
        tvScaleStatus.text = getString(R.string.scale_status, scale)
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
