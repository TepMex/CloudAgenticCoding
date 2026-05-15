package com.tepmex.ankidroidllm.ui

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tepmex.ankidroidllm.AnkiLlmApp
import com.tepmex.ankidroidllm.R
import com.tepmex.ankidroidllm.data.AnkiContract
import com.tepmex.ankidroidllm.data.AnkiVocabularyRepository
import com.tepmex.ankidroidllm.data.AppPreferences
import com.tepmex.ankidroidllm.data.StoryDeckFieldRow
import com.tepmex.ankidroidllm.databinding.ActivitySettingsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var deckFieldAdapter: DeckFieldRowsAdapter
    private lateinit var prefs: AppPreferences
    private lateinit var ankiRepo: AnkiVocabularyRepository
    private lateinit var allDecksLabel: String

    private val ankiPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshAnkiListsUi() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = (application as AnkiLlmApp).preferences
        ankiRepo = AnkiVocabularyRepository(this)
        allDecksLabel = getString(R.string.all_decks)

        deckFieldAdapter = DeckFieldRowsAdapter(
            repo = ankiRepo,
            scope = lifecycleScope,
            allDecksLabel = allDecksLabel,
            initialRows = emptyList(),
        )
        binding.deckFieldRowsRecycler.layoutManager = LinearLayoutManager(this)
        binding.deckFieldRowsRecycler.adapter = deckFieldAdapter

        binding.grantAnkiPermissionButton.setOnClickListener {
            ankiPermissionLauncher.launch(AnkiContract.READ_WRITE_PERMISSION)
        }

        lifecycleScope.launch {
            val s = prefs.settings.first()
            binding.switchRemoteLlm.isChecked = s.useRemoteLlm
            binding.inputBaseUrl.setText(s.llmBaseUrl)
            binding.inputToken.setText(s.llmToken)
            binding.inputRemoteModel.setText(s.remoteModelName)
            binding.inputPrompt.setText(s.systemPrompt)
            binding.inputModelUrl.setText(s.litertModelDownloadUrl)

            deckFieldAdapter.replaceAll(s.deckFieldRows)
            refreshAnkiListsUi()
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

    override fun onResume() {
        super.onResume()
        if (::deckFieldAdapter.isInitialized) {
            refreshAnkiListsUi()
        }
    }

    /**
     * Reloads deck names and field dropdowns when AnkiDroid is installed and permission is granted,
     * including after returning from the permission dialog or from the main screen.
     */
    private fun refreshAnkiListsUi() {
        val installed = ankiRepo.hasAnkiInstalled()
        val permitted = ankiRepo.hasAnkiPermission()
        binding.ankiDataHint.isVisible = installed && !permitted
        binding.grantAnkiPermissionButton.isVisible = installed && !permitted

        if (!installed || !permitted) {
            deckFieldAdapter.deckChoices = emptyList()
            return
        }

        lifecycleScope.launch {
            deckFieldAdapter.deckChoices = ankiRepo.loadAllDeckNames()
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
