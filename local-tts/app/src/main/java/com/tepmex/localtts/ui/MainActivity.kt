package com.tepmex.localtts.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tepmex.localtts.R
import com.tepmex.localtts.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.speakButton.setOnClickListener {
            val speakerId = binding.speakerSpinner.selectedItemPosition
            viewModel.speak(binding.inputText.text?.toString().orEmpty(), speakerId)
        }
        binding.stopButton.setOnClickListener { viewModel.stop() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.statusMessage.isNotBlank()) {
                        binding.statusText.text = state.statusMessage
                    }
                    binding.progressBar.isVisible = state.loading
                    binding.stopButton.isVisible = state.loading
                    binding.speakButton.isEnabled = !state.loading
                }
            }
        }
    }
}
