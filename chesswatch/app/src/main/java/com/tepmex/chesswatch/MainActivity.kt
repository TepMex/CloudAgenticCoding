package com.tepmex.chesswatch

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.tepmex.chesswatch.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val viewModel: MainViewModel by viewModels()

    private val adapter =
        ActivityGridAdapter(
            onSelect = { viewModel.select(it) },
            onDeleteRequest = { id -> maybeDelete(id) },
            displayMs = { viewModel.displayMsFor(it) },
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val columns = gridSpanCount()
        binding.recycler.layoutManager = GridLayoutManager(this, columns)
        binding.recycler.setHasFixedSize(true)
        binding.recycler.adapter = adapter
        binding.recycler.itemAnimator = null

        binding.fabAdd.setOnClickListener { showAddDialog() }

        binding.toolbar.inflateMenu(R.menu.main_toolbar)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_reset -> {
                    viewModel.resetAll()
                    true
                }
                else -> false
            }
        }

        var lastItems: List<TrackedActivity>? = null
        var lastSelected: String? = null
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { s ->
                    if (lastItems != s.items) {
                        lastItems = s.items
                        adapter.submitList(s.items)
                    }
                    if (lastSelected != s.selectedId) {
                        lastSelected = s.selectedId
                        adapter.selectedId = s.selectedId
                    }
                    adapter.updateTiming { viewModel.displayMsFor(it) }
                }
            }
        }
    }

    override fun onPause() {
        viewModel.persist()
        super.onPause()
    }

    private fun gridSpanCount(): Int {
        val w = resources.configuration.screenWidthDp
        return if (w >= 480) 3 else 2
    }

    private fun showAddDialog() {
        val wrap = LayoutInflater.from(this).inflate(R.layout.dialog_add_activity, binding.root, false)
        val input = wrap.findViewById<TextInputEditText>(R.id.input_name)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_activity_title)
            .setView(wrap)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.add) { _, _ ->
                val name = input.text?.toString().orEmpty()
                viewModel.addActivity(name)
            }
            .show()
        input.requestFocus()
    }

    private fun maybeDelete(id: String) {
        if (id == ActivityStore.IDLE_ID) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_activity_title)
            .setMessage(R.string.delete_activity_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteActivity(id) }
            .show()
    }
}
