package com.tepmex.ankientertainer.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tepmex.ankientertainer.AnkiEntertainerApp
import com.tepmex.ankientertainer.data.AppPreferences
import com.tepmex.ankientertainer.data.encodeModelLines
import com.tepmex.ankientertainer.data.parseModelLines
import com.tepmex.ankientertainer.databinding.ActivitySettingsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = (application as AnkiEntertainerApp).preferences

        lifecycleScope.launch {
            val settings = prefs.settings.first()
            binding.inputBaseUrl.setText(settings.llmBaseUrl)
            binding.inputToken.setText(settings.llmToken)
            binding.inputModels.setText(encodeModelLines(settings.modelNames))
            binding.inputChunkPrompt.setText(settings.chunkPrompt)
            binding.inputChunkCount.setText(settings.chunkCount.toString())
        }

        binding.saveButton.setOnClickListener {
            lifecycleScope.launch {
                prefs.update { current ->
                    val count = binding.inputChunkCount.text?.toString()?.toIntOrNull()
                        ?: AppPreferences.DEFAULT_CHUNK_COUNT
                    current.copy(
                        llmBaseUrl = binding.inputBaseUrl.text?.toString().orEmpty().trim(),
                        llmToken = binding.inputToken.text?.toString().orEmpty(),
                        modelNames = parseModelLines(binding.inputModels.text?.toString().orEmpty()),
                        chunkPrompt = binding.inputChunkPrompt.text?.toString().orEmpty()
                            .ifBlank { AppPreferences.DEFAULT_CHUNK_PROMPT },
                        chunkCount = count,
                    )
                }
                finish()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
