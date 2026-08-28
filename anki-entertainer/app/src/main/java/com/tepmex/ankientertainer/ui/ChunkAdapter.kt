package com.tepmex.ankientertainer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tepmex.ankientertainer.R
import com.tepmex.ankientertainer.databinding.ItemChunkBinding

class ChunkAdapter(
    private val onToggleLike: (String) -> Unit,
) : ListAdapter<TextChunk, ChunkAdapter.ChunkViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChunkViewHolder {
        val binding = ItemChunkBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChunkViewHolder(binding, onToggleLike)
    }

    override fun onBindViewHolder(holder: ChunkViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ChunkViewHolder(
        private val binding: ItemChunkBinding,
        private val onToggleLike: (String) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(chunk: TextChunk) {
            binding.chunkText.text = chunk.text
            binding.likedBadge.isVisible = chunk.isLiked
            binding.modelText.text = chunk.modelName.orEmpty()
            binding.modelText.isVisible = !chunk.modelName.isNullOrBlank()
            binding.likeButton.isVisible = chunk.likeable
            binding.likeButton.text = binding.root.context.getString(
                if (chunk.isLiked) R.string.unlike_chunk else R.string.like_chunk,
            )
            binding.likeButton.setOnClickListener {
                if (chunk.likeable) onToggleLike(chunk.id)
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<TextChunk>() {
        override fun areItemsTheSame(oldItem: TextChunk, newItem: TextChunk): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: TextChunk, newItem: TextChunk): Boolean =
            oldItem == newItem
    }
}
