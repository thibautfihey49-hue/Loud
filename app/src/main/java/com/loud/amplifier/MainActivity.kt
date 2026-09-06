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
    private lateinit var seekNoiseReduction: SeekBar
    private lateinit var txtAmplification: TextView
    private lateinit var txtNoiseReduction: TextView

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            toggleAudio()
        } else {
            Toast.makeText(this, "❌ Autorisation micro requise", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioProcessor = AudioProcessor()

        btnToggle = findViewById(R.id.btnToggle)
        seekAmplification = findViewById(R.id.seekAmplification)
        seekNoiseReduction = findViewById(R.id.seekNoiseReduction)
        txtAmplification = findViewById(R.id.txtAmplification)
        txtNoiseReduction = findViewById(R.id.txtNoiseReduction)

        btnToggle.setOnClickListener { toggleAudio() }

        seekAmplification.apply {
            max = 290
            progress = 70
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    audioProcessor.amplification = 1f + p / 10f
                    txtAmplification.text = "Amplification: ×${String.format("%.1f", audioProcessor.amplification)}"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: SeekBar?) = Unit
            })
        }

        seekNoiseReduction.apply {
            max = 100
            progress = 70
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    audioProcessor.noiseReduction = p / 100f
                    txtNoiseReduction.text = "Réduction bruit: ${(p / 100f * 100).toInt()}%"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: SeekBar?) = Unit
            })
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
                Toast.makeText(this, "🔊 Amplificateur démarré ! Casque OBLIGATOIRE", Toast.LENGTH_SHORT).show()
            } else {
                requestPermission.launch(Manifest.permission.RECORD_AUDIO)
                return
            }
        }
        updateUI()
    }

    private fun updateUI() {
        btnToggle.text = if (isRunning) "⏹ ARRÊTER" else "▶ DÉMARRER"
        btnToggle.setBackgroundColor(if (isRunning) 0xFFFF5252.toInt() else 0xFF4CAF50.toInt())
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRunning) audioProcessor.stop()
    }
}
