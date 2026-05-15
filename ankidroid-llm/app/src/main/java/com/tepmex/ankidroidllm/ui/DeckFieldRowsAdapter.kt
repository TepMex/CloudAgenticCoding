package com.tepmex.ankidroidllm.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tepmex.ankidroidllm.data.AnkiVocabularyRepository
import com.tepmex.ankidroidllm.data.StoryDeckFieldRow
import com.tepmex.ankidroidllm.databinding.ItemDeckFieldRowBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeckFieldRowsAdapter(
    private val repo: AnkiVocabularyRepository,
    private val scope: CoroutineScope,
    private val allDecksLabel: String,
    initialRows: List<StoryDeckFieldRow>,
) : RecyclerView.Adapter<DeckFieldRowsAdapter.VH>() {

    private val rows: MutableList<StoryDeckFieldRow> =
        if (initialRows.isEmpty()) {
            mutableListOf(StoryDeckFieldRow("", ""))
        } else {
            initialRows.map { it.copy() }.toMutableList()
        }

    var deckChoices: List<String> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun currentRows(): List<StoryDeckFieldRow> = rows.map { it.copy() }

    fun replaceAll(newRows: List<StoryDeckFieldRow>) {
        rows.clear()
        if (newRows.isEmpty()) {
            rows.add(StoryDeckFieldRow("", ""))
        } else {
            newRows.forEach { rows.add(it.copy()) }
        }
        notifyDataSetChanged()
    }

    fun addRow() {
        rows.add(StoryDeckFieldRow("", ""))
        notifyItemInserted(rows.size - 1)
    }

    fun removeRowAt(position: Int) {
        if (rows.size <= 1 || position !in rows.indices) return
        rows.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, rows.size - position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemDeckFieldRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount(): Int = rows.size

    inner class VH(
        private val binding: ItemDeckFieldRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        private var fieldsJob: Job? = null

        fun bind(position: Int) {
            fieldsJob?.cancel()

            val row = rows[position]
            val deckItems = buildList {
                add(allDecksLabel)
                addAll(deckChoices)
            }
            binding.deckDropdown.setAdapter(
                ArrayAdapter(binding.root.context, android.R.layout.simple_dropdown_item_1line, deckItems),
            )
            val deckDisplay = if (row.deckName.isBlank()) allDecksLabel else row.deckName
            binding.deckDropdown.setText(deckDisplay, false)

            binding.fieldDropdown.setText(row.fieldName, false)

            binding.removeRowButton.isEnabled = rows.size > 1
            binding.removeRowButton.alpha = if (rows.size > 1) 1f else 0.35f

            binding.deckDropdown.setOnItemClickListener { parent, _, idx, _ ->
                val label = parent.getItemAtPosition(idx) as String
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || pos !in rows.indices) return@setOnItemClickListener
                val stored = if (label == allDecksLabel) "" else label
                rows[pos] = rows[pos].copy(deckName = stored)
                reloadFieldsForPosition(pos)
            }

            binding.fieldDropdown.setOnItemClickListener { parent, _, idx, _ ->
                val label = parent.getItemAtPosition(idx) as String
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || pos !in rows.indices) return@setOnItemClickListener
                rows[pos] = rows[pos].copy(fieldName = label)
            }

            binding.removeRowButton.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    removeRowAt(pos)
                }
            }

            reloadFieldsForPosition(position)
        }

        private fun reloadFieldsForPosition(rowIndex: Int) {
            fieldsJob?.cancel()
            fieldsJob = scope.launch {
                val deck = rows.getOrNull(rowIndex)?.deckName ?: ""
                val fields = repo.loadDistinctFieldNamesForDeck(deck)
                withContext(Dispatchers.Main) {
                    if (bindingAdapterPosition != rowIndex) return@withContext
                    binding.fieldDropdown.setAdapter(
                        ArrayAdapter(binding.root.context, android.R.layout.simple_dropdown_item_1line, fields),
                    )
                    val cur = rows.getOrNull(rowIndex)?.fieldName.orEmpty()
                    if (cur.isNotBlank() && fields.any { it.equals(cur, ignoreCase = true) }) {
                        binding.fieldDropdown.setText(cur, false)
                    }
                }
            }
        }
    }
}
