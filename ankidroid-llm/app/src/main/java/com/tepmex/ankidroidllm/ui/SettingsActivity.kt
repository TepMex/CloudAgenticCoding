package com.tepmex.ankidroidllm.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tepmex.ankidroidllm.AnkiLlmApp
import com.tepmex.ankidroidllm.R
import com.tepmex.ankidroidllm.data.AnkiVocabularyRepository
import com.tepmex.ankidroidllm.data.AppPreferences
import com.tepmex.ankidroidllm.data.StoryDeckFieldRow
import com.tepmex.ankidroidllm.databinding.ActivitySettingsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var deckFieldAdapter: DeckFieldRowsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = (application as AnkiLlmApp).preferences
        val repo = AnkiVocabularyRepository(this)
        val allDecks = getString(R.string.all_decks)

        deckFieldAdapter = DeckFieldRowsAdapter(
            repo = repo,
            scope = lifecycleScope,
            allDecksLabel = allDecks,
            initialRows = emptyList(),
        )
        binding.deckFieldRowsRecycler.layoutManager = LinearLayoutManager(this)
        binding.deckFieldRowsRecycler.adapter = deckFieldAdapter

        lifecycleScope.launch {
            val s = prefs.settings.first()
            binding.switchRemoteLlm.isChecked = s.useRemoteLlm
            binding.inputBaseUrl.setText(s.llmBaseUrl)
            binding.inputToken.setText(s.llmToken)
            binding.inputRemoteModel.setText(s.remoteModelName)
            binding.inputPrompt.setText(s.systemPrompt)
            binding.inputModelUrl.setText(s.litertModelDownloadUrl)

            deckFieldAdapter.replaceAll(s.deckFieldRows)

            if (repo.hasAnkiInstalled() && repo.hasAnkiPermission()) {
                binding.ankiDataHint.isVisible = false
                deckFieldAdapter.deckChoices = repo.loadAllDeckNames()
            } else {
                binding.ankiDataHint.isVisible = true
            }
        }

        binding.addDeckFieldRowButton.setOnClickListener {
            deckFieldAdapter.addRow()
        }

        binding.saveButton.setOnClickListener {
            lifecycleScope.launch {
                prefs.update { cur ->
                    val rows = normalizeRowsForPersistence(deckFieldAdapter.currentRows())
                    cur.copy(
                        useRemoteLlm = binding.switchRemoteLlm.isChecked,
                        llmBaseUrl = binding.inputBaseUrl.text?.toString().orEmpty(),
                        llmToken = binding.inputToken.text?.toString().orEmpty(),
                        remoteModelName = binding.inputRemoteModel.text?.toString().orEmpty(),
                        systemPrompt = binding.inputPrompt.text?.toString().orEmpty()
                            .ifBlank { AppPreferences.DEFAULT_PROMPT },
                        litertModelDownloadUrl = binding.inputModelUrl.text?.toString().orEmpty()
                            .ifBlank { AppPreferences.DEFAULT_MODEL_URL },
                        deckFieldRows = rows,
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

    private fun normalizeRowsForPersistence(rows: List<StoryDeckFieldRow>): List<StoryDeckFieldRow> {
        if (rows.size == 1) {
            val r = rows[0]
            if (r.deckName.isBlank() && r.fieldName.isBlank()) {
                return emptyList()
            }
        }
        return rows
    }
}
