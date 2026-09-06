package com.loud.amplifier

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var audioProcessor: AudioProcessor
    private var isRunning = false
    private lateinit var btnToggle: Button
    private lateinit var seekAmplification: SeekBar
    private lateinit var seekBass: SeekBar
    private lateinit var txtAmplification: TextView
    private lateinit var txtBass: TextView
    private lateinit var btnCompressor: Button
    private lateinit var btnLimiter: Button

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) toggleAudio()
        else Toast.makeText(this, "❌ Autorisation micro requise", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioProcessor = AudioProcessor()

        btnToggle = findViewById(R.id.btnToggle)
        seekAmplification = findViewById(R.id.seekAmplification)
        seekBass = findViewById(R.id.seekBass)
        txtAmplification = findViewById(R.id.txtAmplification)
        txtBass = findViewById(R.id.txtBass)
        btnCompressor = findViewById(R.id.btnCompressor)
        btnLimiter = findViewById(R.id.btnLimiter)

        btnToggle.setOnClickListener { toggleAudio() }

        seekAmplification.apply {
            max = 490
            progress = 240
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    audioProcessor.amplification = 1f + p / 10f
                    txtAmplification.text = "🔊 Amplification: ×${String.format("%.1f", audioProcessor.amplification)}"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: SeekBar?) = Unit
            })
        }

        seekBass.apply {
            max = 30
            progress = 20
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    audioProcessor.bassBoost = p / 10f
                    txtBass.text = "🎵 Boost graves: ${String.format("%.1f", audioProcessor.bassBoost)}x"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: SeekBar?) = Unit
            })
        }

        btnCompressor.apply {
            isSelected = true
            setText("✅ Compresseur ON")
            setOnClickListener {
                isSelected = !isSelected
                audioProcessor.compressorEnabled = isSelected
                text = if (isSelected) "✅ Compresseur ON" else "❌ Compresseur OFF"
            }
        }

        btnLimiter.apply {
            isSelected = true
            setText("✅ Limiteur ON")
            setOnClickListener {
                isSelected = !isSelected
                audioProcessor.limiterEnabled = isSelected
                text = if (isSelected) "✅ Limiteur ON" else "❌ Limiteur OFF"
            }
        }
    }

    private fun toggleAudio() {
        if (isRunning) {
            audioProcessor.stop()
            isRunning = false
        } else {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                audioProcessor.start()
                isRunning = true
                Toast.makeText(this, "🔊 LOUDEST MODE ACTIF ! CASQUE OBLIGATOIRE ⚠️", Toast.LENGTH_LONG).show()
            } else {
                requestPermission.launch(Manifest.permission.RECORD_AUDIO)
                return
            }
        }
        updateUI()
    }

    private fun updateUI() {
        btnToggle.text = if (isRunning) "⏹ COUPER" else "▶ MAXIMUM"
        btnToggle.setBackgroundColor(if (isRunning) 0xFFFF5252.toInt() else 0xFF4CAF50.toInt())
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRunning) audioProcessor.stop()
    }
}
