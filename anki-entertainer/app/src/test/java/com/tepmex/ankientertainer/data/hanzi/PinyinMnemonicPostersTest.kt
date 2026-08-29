package com.tepmex.ankientertainer.data.hanzi

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.tepmex.ankientertainer.ui.ChunkAdapter
import com.tepmex.ankientertainer.ui.PosterBitmapLoader
import com.tepmex.ankientertainer.ui.TextChunk
import com.tepmex.ankientertainer.ui.mergeSessionChunks
import com.tepmex.ankientertainer.ui.posterChunks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PinyinMnemonicPostersTest {

    @Test
    fun catalogIsFinalsThenInitialsWithStableIdsAndAssetPaths() {
        assertEquals(
            listOf("poster:pinyin-finals", "poster:pinyin-initials"),
            PinyinMnemonicPosters.ALL.map { it.id },
        )
        assertEquals("Pinyin finals", PinyinMnemonicPosters.FINALS.title)
        assertEquals("Pinyin initials", PinyinMnemonicPosters.INITIALS.title)
        assertEquals("posters/pinyin_finals.jpg", PinyinMnemonicPosters.FINALS.assetPath)
        assertEquals("posters/pinyin_initials.jpg", PinyinMnemonicPosters.INITIALS.assetPath)
        assertEquals("local poster", PinyinMnemonicPosters.MODEL_LABEL)
    }

    @Test
    fun posterChunksAreNotLikeableAndCarryImageAssets() {
        val chunks = posterChunks()
        assertEquals(2, chunks.size)
        assertEquals("poster:pinyin-finals", chunks[0].id)
        assertEquals("poster:pinyin-initials", chunks[1].id)
        chunks.forEach { chunk ->
            assertFalse(chunk.likeable)
            assertFalse(chunk.isLiked)
            assertEquals(PinyinMnemonicPosters.MODEL_LABEL, chunk.modelName)
            val asset = chunk.imageAsset
            assertNotNull(asset)
            assertTrue(asset!!.startsWith("posters/"))
            assertTrue(asset.endsWith(".jpg"))
        }
    }

    @Test
    fun sessionPutsPostersAfterCompositionAndBeforeLikedStories() {
        val composition = listOf(chunk("c", "composition"))
        val posters = posterChunks()
        val liked = listOf(chunk("l", "liked", liked = true))
        val stories = listOf(chunk("s", "story"))
        val merged = mergeSessionChunks(composition, posters, liked, stories)
        assertEquals(
            listOf("c", "poster:pinyin-finals", "poster:pinyin-initials", "l", "s"),
            merged.map { it.id },
        )
        assertEquals("composition", merged.first().text)
        assertEquals("story", merged.last().text)
    }

    @Test
    fun postersStillLeadWhenThereAreNoCompositionCards() {
        val merged = mergeSessionChunks(
            composition = emptyList(),
            posters = posterChunks(),
            liked = emptyList(),
            stories = listOf(chunk("s", "story")),
        )
        assertEquals("poster:pinyin-finals", merged.first().id)
        assertEquals("story", merged.last().text)
        assertEquals(3, merged.size)
    }

    private fun chunk(id: String, text: String, liked: Boolean = false) = TextChunk(
        id = id,
        text = text,
        isLiked = liked,
        modelName = "test",
    )
}

@RunWith(RobolectricTestRunner::class)
class PinyinMnemonicPosterAssetTest {
    @Test
    fun bundledJpegAssetsExistAndDecode() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PinyinMnemonicPosters.ALL.forEach { poster ->
            val bytes = context.assets.open(poster.assetPath).use { it.readBytes() }
            assertTrue("${poster.assetPath} too small", bytes.size > 10_000)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertNotNull(poster.assetPath, bitmap)
            assertTrue("${poster.assetPath} width", bitmap!!.width > 100)
            assertTrue("${poster.assetPath} height", bitmap.height > 100)
        }
    }

    @Test
    fun downsamplesWidePosterToFitPreviewWidth() {
        assertEquals(1, PosterBitmapLoader.sampleSize(400, 800))
        assertEquals(2, PosterBitmapLoader.sampleSize(2000, 800))
        assertEquals(4, PosterBitmapLoader.sampleSize(4000, 800))
        assertEquals(1, PosterBitmapLoader.sampleSize(0, 800))
    }

    @Test
    fun adapterUsesPosterViewTypeForImageChunks() {
        val adapter = ChunkAdapter(onToggleLike = {}, onOpenPoster = { _, _ -> })
        val latch = CountDownLatch(1)
        adapter.submitList(
            listOf(
                TextChunk("c", "comp", false, "local composition", likeable = false),
                posterChunks()[0],
                TextChunk("s", "story", false, "gpt"),
            ),
        ) { latch.countDown() }
        ShadowLooper.shadowMainLooper().idle()
        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertEquals(ChunkAdapter.TYPE_TEXT, adapter.getItemViewType(0))
        assertEquals(ChunkAdapter.TYPE_POSTER, adapter.getItemViewType(1))
        assertEquals(ChunkAdapter.TYPE_TEXT, adapter.getItemViewType(2))
    }
}

