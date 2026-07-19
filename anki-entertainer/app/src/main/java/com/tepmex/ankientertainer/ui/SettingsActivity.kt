package com.tepmex.ankientertainer.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tepmex.ankientertainer.AnkiEntertainerApp
import com.tepmex.ankientertainer.R
import com.tepmex.ankientertainer.data.AppPreferences
import com.tepmex.ankientertainer.data.encodeModelLines
import com.tepmex.ankientertainer.data.hanzi.DefaultPromptTemplateEngine
import com.tepmex.ankientertainer.data.hanzi.PromptPlaceholders
import com.tepmex.ankientertainer.data.parseModelLines
import com.tepmex.ankientertainer.databinding.ActivitySettingsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: AppPreferences
    private lateinit var app: AnkiEntertainerApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        app = application as AnkiEntertainerApp
        prefs = app.preferences

        lifecycleScope.launch {
            val settings = prefs.settings.first()
            binding.inputBaseUrl.setText(settings.llmBaseUrl)
            binding.inputToken.setText(settings.llmToken)
            binding.inputModels.setText(encodeModelLines(settings.modelNames))
            binding.inputChunkPrompt.setText(settings.chunkPrompt)
            binding.inputChunkCount.setText(settings.chunkCount.toString())
            binding.inputPreviewQuery.setText("你好")
            refreshPlaceholderWarning()
            refreshDatasetStatus()
        }

        binding.inputChunkPrompt.addTextChangedListener(SimpleWatcher { refreshPlaceholderWarning() })

        binding.previewButton.setOnClickListener {
            lifecycleScope.launch {
                val template = binding.inputChunkPrompt.text?.toString().orEmpty()
                    .ifBlank { AppPreferences.DEFAULT_CHUNK_PROMPT }
                val query = binding.inputPreviewQuery.text?.toString().orEmpty().ifBlank { "你好" }
                val result = app.promptTemplateEngine.expand(template, query)
                val warningBlock = if (result.warnings.isNotEmpty()) {
                    getString(R.string.preview_warnings, result.warnings.joinToString("\n")) + "\n\n"
                } else {
                    ""
                }
                binding.previewOutput.text = warningBlock + result.prompt
            }
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

    private fun refreshPlaceholderWarning() {
        val template = binding.inputChunkPrompt.text?.toString().orEmpty()
        val unknown = DefaultPromptTemplateEngine.findUnknownPlaceholders(template)
        if (unknown.isEmpty()) {
            binding.placeholderWarning.visibility = View.GONE
            binding.placeholderWarning.text = ""
        } else {
            binding.placeholderWarning.visibility = View.VISIBLE
            binding.placeholderWarning.text = getString(
                R.string.unknown_placeholders_warning,
                unknown.joinToString(", ") { PromptPlaceholders.token(it) },
            )
        }
    }

    private suspend fun refreshDatasetStatus() {
        val status = app.hanziMetadataRepository.datasetStatus()
        binding.datasetStatus.text = buildString {
            append(status.message)
            if (status.buildTimestamp != null) {
                append("\nBuilt: ")
                append(status.buildTimestamp)
            }
            append("\nSupported: ")
            append(PromptPlaceholders.SUPPORTED.joinToString(", ") { PromptPlaceholders.token(it) })
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private class SimpleWatcher(private val onChange: () -> Unit) : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = onChange()
    }
}
