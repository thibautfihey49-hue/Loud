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
    private lateinit var seekNoiseGate: SeekBar
    private lateinit var txtAmplification: TextView
    private lateinit var txtNoiseGate: TextView

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
        seekNoiseGate = findViewById(R.id.seekNoiseGate)
        txtAmplification = findViewById(R.id.txtAmplification)
        txtNoiseGate = findViewById(R.id.txtNoiseGate)

        btnToggle.setOnClickListener { toggleAudio() }

        seekAmplification.apply {
            max = 150   // ×1 à ×16 — propre
            progress = 11
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    audioProcessor.amplification = 1f + p
                    txtAmplification.text = "🔊 Amplification: ×${String.format("%.0f", audioProcessor.amplification)}"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: SeekBar?) = Unit
            })
        }

        seekNoiseGate.apply {
            max = 30
            progress = 15
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    audioProcessor.noiseGate = p / 1000f
                    txtNoiseGate.text = "🎚️ Réduction bruit: ${(p/3f).toInt()}%"
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
                Toast.makeText(this, "✅ Actif — CASQUE OBLIGATOIRE", Toast.LENGTH_SHORT).show()
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
