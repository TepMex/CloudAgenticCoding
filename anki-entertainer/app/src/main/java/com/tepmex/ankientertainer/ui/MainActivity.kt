package com.tepmex.ankientertainer.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.tepmex.ankientertainer.R
import com.tepmex.ankientertainer.data.DeepLinkParser
import com.tepmex.ankientertainer.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: EntertainerViewModel by viewModels()
    private lateinit var chunkAdapter: ChunkAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        chunkAdapter = ChunkAdapter(
            onToggleLike = viewModel::toggleLike,
            onOpenPoster = { asset, title ->
                startActivity(PosterViewerActivity.intent(this, asset, title))
            },
        )
        binding.chunksRecycler.layoutManager = LinearLayoutManager(this)
        binding.chunksRecycler.adapter = chunkAdapter

        binding.regenerateButton.setOnClickListener { viewModel.regenerate() }
        binding.stopButton.setOnClickListener { viewModel.stopGeneration() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val hasVocab = !state.vocab.isNullOrBlank()
                    binding.vocabText.isVisible = hasVocab
                    binding.vocabText.text = state.vocab.orEmpty()
                    binding.launchHint.isVisible = !hasVocab
                    binding.chunksRecycler.isVisible = hasVocab
                    binding.regenerateButton.isVisible = hasVocab
                    binding.stopButton.isVisible = state.loading
                    binding.progressBar.isVisible = state.loading
                    binding.regenerateButton.isEnabled = hasVocab && !state.loading
                    binding.statusText.text = state.statusMessage.ifBlank {
                        getString(R.string.status_idle)
                    }
                    chunkAdapter.submitList(state.chunks)
                }
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val vocab = DeepLinkParser.parseVocab(intent?.data) ?: return
        viewModel.openVocab(vocab)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_SETTINGS, 0, R.string.action_settings)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_SETTINGS) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val MENU_SETTINGS = 1
    }
}
