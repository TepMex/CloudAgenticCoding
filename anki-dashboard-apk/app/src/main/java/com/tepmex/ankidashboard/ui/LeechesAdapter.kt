package com.tepmex.ankidashboard.ui

import android.text.Html
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tepmex.ankidashboard.data.LeechCard
import com.tepmex.ankidashboard.databinding.ItemLeechBinding

class LeechesAdapter : RecyclerView.Adapter<LeechesAdapter.Holder>() {

    private var items: List<LeechCard> = emptyList()
    private var fieldForDeck: Map<String, String> = emptyMap()

    fun submit(leeches: List<LeechCard>, fieldByDeck: Map<String, String>) {
        items = leeches.sortedByDescending { it.reviewCount }
        fieldForDeck = fieldByDeck
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemLeechBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val card = items[position]
        val fieldName = fieldForDeck[card.deckName].orEmpty()
        val raw = card.fields[fieldName].orEmpty()
        val plain = Html.fromHtml(raw, Html.FROM_HTML_MODE_COMPACT).toString()
            .replace('\n', ' ')
            .trim()
        val text = if (plain.length > 120) plain.take(117) + "..." else plain
        holder.binding.leechText.text = text.ifBlank { "Untitled card" }
        holder.binding.leechReviews.text =
            holder.binding.root.context.getString(
                com.tepmex.ankidashboard.R.string.leech_reviews,
                card.reviewCount,
            )
    }

    override fun getItemCount(): Int = items.size

    class Holder(val binding: ItemLeechBinding) : RecyclerView.ViewHolder(binding.root)
}
