package com.tepmex.ankientertainer.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tepmex.ankientertainer.AnkiEntertainerApp
import com.tepmex.ankientertainer.R
import com.tepmex.ankientertainer.data.AppPreferences
import com.tepmex.ankientertainer.data.LlmProvider
import com.tepmex.ankientertainer.data.encodeModelLines
import com.tepmex.ankientertainer.data.hanzi.DefaultPromptTemplateEngine
import com.tepmex.ankientertainer.data.hanzi.PromptPlaceholders
import com.tepmex.ankientertainer.data.parseModelLines
import com.tepmex.ankientertainer.databinding.ActivitySettingsBinding
import com.tepmex.ankientertainer.databinding.ItemLlmProviderBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: AppPreferences
    private lateinit var app: AnkiEntertainerApp
    private val providerBindings = mutableListOf<ItemLlmProviderBinding>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        app = application as AnkiEntertainerApp
        prefs = app.preferences

        lifecycleScope.launch {
            val settings = prefs.settings.first()
            replaceProviders(settings.providers)
            binding.inputChunkPrompt.setText(settings.chunkPrompt)
            binding.inputChunkCount.setText(settings.chunkCount.toString())
            binding.inputPreviewQuery.setText("你好")
            refreshPlaceholderWarning()
            refreshDatasetStatus()
        }

        binding.inputChunkPrompt.addTextChangedListener(SimpleWatcher { refreshPlaceholderWarning() })

        binding.addProviderButton.setOnClickListener {
            addProviderRow(LlmProvider())
            renumberProviderTitles()
        }

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
                        providers = collectProviders().ifEmpty { listOf(LlmProvider()) },
                        chunkPrompt = binding.inputChunkPrompt.text?.toString().orEmpty()
                            .ifBlank { AppPreferences.DEFAULT_CHUNK_PROMPT },
                        chunkCount = count,
                    )
                }
                finish()
            }
        }
    }

    private fun replaceProviders(providers: List<LlmProvider>) {
        binding.providersContainer.removeAllViews()
        providerBindings.clear()
        val initial = providers.ifEmpty { listOf(LlmProvider()) }
        for (provider in initial) {
            addProviderRow(provider)
        }
        renumberProviderTitles()
    }

    private fun addProviderRow(provider: LlmProvider) {
        val item = ItemLlmProviderBinding.inflate(
            LayoutInflater.from(this),
            binding.providersContainer,
            false,
        )
        item.inputBaseUrl.setText(provider.baseUrl)
        item.inputToken.setText(provider.token)
        item.inputProject.setText(provider.project)
        item.inputModels.setText(encodeModelLines(provider.modelNames))
        item.removeProviderButton.setOnClickListener {
            if (providerBindings.size <= 1) {
                // Keep at least one empty slot so the user can still configure a provider.
                item.inputBaseUrl.setText("")
                item.inputToken.setText("")
                item.inputProject.setText("")
                item.inputModels.setText("")
                return@setOnClickListener
            }
            binding.providersContainer.removeView(item.root)
            providerBindings.remove(item)
            renumberProviderTitles()
        }
        providerBindings.add(item)
        binding.providersContainer.addView(item.root)
    }

    private fun renumberProviderTitles() {
        providerBindings.forEachIndexed { index, item ->
            item.providerTitle.text = getString(R.string.provider_title, index + 1)
        }
    }

    private fun collectProviders(): List<LlmProvider> =
        providerBindings.map { item ->
            LlmProvider(
                baseUrl = item.inputBaseUrl.text?.toString().orEmpty().trim(),
                token = item.inputToken.text?.toString().orEmpty(),
                modelNames = parseModelLines(item.inputModels.text?.toString().orEmpty()),
                project = item.inputProject.text?.toString().orEmpty().trim(),
            )
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
