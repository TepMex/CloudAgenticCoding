package com.tepmex.ankidashboard.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.tepmex.ankidashboard.R

class DeckChipsAdapter(
    private val onSelectionChanged: (Set<String>) -> Unit,
) : RecyclerView.Adapter<DeckChipsAdapter.Holder>() {

    private var deckNames: List<String> = emptyList()
    private var selected: MutableSet<String> = linkedSetOf()

    fun submitDecks(names: List<String>, selectedDecks: Set<String>) {
        deckNames = names.sorted()
        selected = selectedDecks.toMutableSet()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val chip = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_deck_chip, parent, false) as Chip
        return Holder(chip)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val name = deckNames[position]
        holder.chip.text = name
        holder.chip.setOnCheckedChangeListener(null)
        holder.chip.isChecked = selected.contains(name)
        holder.chip.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selected.add(name)
            } else {
                selected.remove(name)
            }
            onSelectionChanged(selected.toSet())
        }
    }

    override fun getItemCount(): Int = deckNames.size

    class Holder(val chip: Chip) : RecyclerView.ViewHolder(chip)
}
