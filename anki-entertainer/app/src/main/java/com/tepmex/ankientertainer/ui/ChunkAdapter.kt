package com.tepmex.ankientertainer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tepmex.ankientertainer.R
import com.tepmex.ankientertainer.databinding.ItemChunkBinding
import com.tepmex.ankientertainer.databinding.ItemPosterBinding

class ChunkAdapter(
    private val onToggleLike: (String) -> Unit,
    private val onOpenPoster: (asset: String, title: String) -> Unit,
) : ListAdapter<TextChunk, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).imageAsset != null) TYPE_POSTER else TYPE_TEXT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_POSTER) {
            PosterViewHolder(ItemPosterBinding.inflate(inflater, parent, false), onOpenPoster)
        } else {
            ChunkViewHolder(ItemChunkBinding.inflate(inflater, parent, false), onToggleLike)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is PosterViewHolder -> holder.bind(item)
            is ChunkViewHolder -> holder.bind(item)
        }
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

    class PosterViewHolder(
        private val binding: ItemPosterBinding,
        private val onOpenPoster: (asset: String, title: String) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(chunk: TextChunk) {
            val asset = chunk.imageAsset ?: return
            binding.posterTitle.text = chunk.text
            binding.modelText.text = chunk.modelName.orEmpty()
            val maxWidth = binding.root.resources.displayMetrics.widthPixels
            binding.posterImage.setImageBitmap(
                PosterBitmapLoader.load(binding.root.context, asset, maxWidth),
            )
            val open = { onOpenPoster(asset, chunk.text) }
            binding.root.setOnClickListener { open() }
            binding.posterImage.setOnClickListener { open() }
        }
    }

    private object Diff : DiffUtil.ItemCallback<TextChunk>() {
        override fun areItemsTheSame(oldItem: TextChunk, newItem: TextChunk): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: TextChunk, newItem: TextChunk): Boolean =
            oldItem == newItem
    }

    companion object {
        const val TYPE_TEXT = 0
        const val TYPE_POSTER = 1
    }
}
