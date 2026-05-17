package com.tepmex.chesswatch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.tepmex.chesswatch.databinding.ItemActivityTileBinding

class ActivityGridAdapter(
    private val onSelect: (String) -> Unit,
    private val onDeleteRequest: (String) -> Unit,
    private var displayMs: (TrackedActivity) -> Long,
) : ListAdapter<TrackedActivity, ActivityGridAdapter.TileViewHolder>(Diff) {
    var selectedId: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun updateTiming(displayMs: (TrackedActivity) -> Long) {
        this.displayMs = displayMs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemActivityTileBinding.inflate(inflater, parent, false)
        return TileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TileViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, item.id == selectedId, displayMs(item))
    }

    inner class TileViewHolder(
        private val binding: ItemActivityTileBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: TrackedActivity,
            selected: Boolean,
            ms: Long,
        ) {
            val card = binding.root as MaterialCardView
            val ctx = card.context
            card.setCardBackgroundColor(item.tileColorArgb)
            val d = ctx.resources.displayMetrics.density
            card.strokeWidth = ((if (selected) 2.5f else 1f) * d).toInt()
            card.strokeColor =
                ContextCompat.getColor(
                    ctx,
                    if (selected) R.color.cw_tile_stroke_selected else R.color.cw_tile_stroke,
                )
            binding.activityName.text = item.name
            binding.timer.text = formatElapsed(ms)
            card.setOnClickListener { onSelect(item.id) }
            card.setOnLongClickListener {
                onDeleteRequest(item.id)
                true
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<TrackedActivity>() {
        override fun areItemsTheSame(
            oldItem: TrackedActivity,
            newItem: TrackedActivity,
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: TrackedActivity,
            newItem: TrackedActivity,
        ): Boolean = oldItem == newItem
    }

    companion object {
        fun formatElapsed(totalMs: Long): String {
            val totalSec = totalMs / 1000
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            return if (h > 0) {
                String.format("%d:%02d:%02d", h, m, s)
            } else {
                String.format("%02d:%02d", m, s)
            }
        }
    }
}
