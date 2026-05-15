package com.tepmex.ankidroidllm.ui

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tepmex.ankidroidllm.R
import com.tepmex.ankidroidllm.data.AnkiContract
import com.tepmex.ankidroidllm.databinding.ActivityMainBinding
import io.noties.markwon.Markwon
import io.noties.markwon.core.CorePlugin
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: StoryViewModel by viewModels()
    private lateinit var storyMarkwon: Markwon

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.generateStory()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        storyMarkwon = Markwon.builder(this)
            .usePlugin(CorePlugin.create())
            .build()
        binding.storyText.movementMethod = LinkMovementMethod.getInstance()

        binding.generateButton.setOnClickListener {
            tryStartGeneration()
        }
        binding.stopButton.setOnClickListener {
            viewModel.stopGeneration()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.statusText.text = state.statusMessage
                    binding.progressBar.isVisible = state.loading
                    binding.stopButton.isVisible = state.loading
                    binding.generateButton.isEnabled = !state.loading
                    binding.generateButton.text = getString(
                        if (state.storyText.isNotBlank()) {
                            R.string.regenerate_story
                        } else {
                            R.string.generate_story
                        },
                    )
                    if (state.storyText.isBlank()) {
                        binding.storyText.text = getString(R.string.story_hint)
                    } else {
                        storyMarkwon.setMarkdown(binding.storyText, state.storyText)
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_SETTINGS, 0, R.string.action_settings)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_SETTINGS) {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun tryStartGeneration() {
        val repo = viewModel.ankiRepository
        if (!repo.hasAnkiInstalled()) {
            binding.statusText.text = getString(R.string.error_anki_missing)
            return
        }
        if (repo.hasAnkiPermission()) {
            viewModel.generateStory()
        } else {
            permissionLauncher.launch(AnkiContract.READ_WRITE_PERMISSION)
        }
    }

    companion object {
        private const val MENU_SETTINGS = 1001
    }
}
