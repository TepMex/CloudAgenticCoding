package com.tepmex.ankidroidllm.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tepmex.ankidroidllm.AnkiLlmApp
import com.tepmex.ankidroidllm.data.AppPreferences
import com.tepmex.ankidroidllm.databinding.ActivitySettingsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = (application as AnkiLlmApp).preferences
        lifecycleScope.launch {
            val s = prefs.settings.first()
            binding.switchRemoteLlm.isChecked = s.useRemoteLlm
            binding.inputBaseUrl.setText(s.llmBaseUrl)
            binding.inputToken.setText(s.llmToken)
            binding.inputRemoteModel.setText(s.remoteModelName)
            binding.inputPrompt.setText(s.systemPrompt)
            binding.inputModelUrl.setText(s.litertModelDownloadUrl)
            binding.inputDecks.setText(s.deckNamesCsv)
            binding.inputVocabField.setText(s.vocabFieldName)
        }

        binding.saveButton.setOnClickListener {
            lifecycleScope.launch {
                prefs.update { cur ->
                    cur.copy(
                        useRemoteLlm = binding.switchRemoteLlm.isChecked,
                        llmBaseUrl = binding.inputBaseUrl.text?.toString().orEmpty(),
                        llmToken = binding.inputToken.text?.toString().orEmpty(),
                        remoteModelName = binding.inputRemoteModel.text?.toString().orEmpty(),
                        systemPrompt = binding.inputPrompt.text?.toString().orEmpty()
                            .ifBlank { AppPreferences.DEFAULT_PROMPT },
                        litertModelDownloadUrl = binding.inputModelUrl.text?.toString().orEmpty()
                            .ifBlank { AppPreferences.DEFAULT_MODEL_URL },
                        deckNamesCsv = binding.inputDecks.text?.toString().orEmpty(),
                        vocabFieldName = binding.inputVocabField.text?.toString().orEmpty(),
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
