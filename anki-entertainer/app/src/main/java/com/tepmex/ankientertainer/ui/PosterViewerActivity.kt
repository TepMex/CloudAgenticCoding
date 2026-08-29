package com.tepmex.ankientertainer.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.tepmex.ankientertainer.R
import com.tepmex.ankientertainer.databinding.ActivityPosterViewerBinding

class PosterViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityPosterViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val asset = intent.getStringExtra(EXTRA_ASSET).orEmpty()
        supportActionBar?.title = title.ifBlank { getString(R.string.poster_viewer_title) }

        if (asset.isBlank()) {
            finish()
            return
        }

        val maxWidth = resources.displayMetrics.widthPixels.coerceAtLeast(1080)
        binding.posterImage.setImageBitmap(PosterBitmapLoader.load(this, asset, maxWidth * 2))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_ASSET = "asset"
        const val EXTRA_TITLE = "title"

        fun intent(context: Context, asset: String, title: String): Intent =
            Intent(context, PosterViewerActivity::class.java)
                .putExtra(EXTRA_ASSET, asset)
                .putExtra(EXTRA_TITLE, title)
    }
}
