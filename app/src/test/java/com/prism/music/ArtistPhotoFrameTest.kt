package com.prism.music

import com.prism.music.data.artists.ArtistPhotoFrame
import org.junit.Assert.*
import org.junit.Test

class ArtistPhotoFrameTest {
    @Test fun fillCoversPortraitAndLandscapeAndFitKeepsTheWholePhoto() {
        for ((w, h) in listOf(300f to 100f, 100f to 300f)) {
            val fill = ArtistPhotoFrame()
            val scale = fill.scale(100f, 100f, w, h)
            assertTrue(w * scale >= 100f && h * scale >= 100f)
            val fit = fill.copy(fill = false)
            val fitScale = fit.scale(100f, 100f, w, h)
            assertTrue(w * fitScale <= 100f && h * fitScale <= 100f)
            assertTrue(w * fitScale == 100f || h * fitScale == 100f)
        }
    }

    @Test fun draggingClampsTheFrameAndKeepsItsPositionAtAnotherSize() {
        val start = ArtistPhotoFrame()
        val moved = start.gesture(100f, 100f, 2f, -200f, 30f, 1f, 50f, 50f)
        assertEquals(-1f, moved.x, 0f)
        assertEquals(0f, moved.y, 0f)
        assertEquals(-100f, moved.offset(200f, 100f, moved.x), 0f)
        assertEquals(-200f, moved.offset(400f, 200f, moved.x), 0f)
    }

    @Test fun pinchKeepsTheTouchedPointInPlaceWhenThereIsRoom() {
        val start = ArtistPhotoFrame()
        val zoomed = start.gesture(100f, 100f, 1f, 0f, 0f, 2f, 25f, 40f)
        assertEquals(2f, zoomed.zoom, 0f)
        assertEquals(-25f, zoomed.offset(200f, 100f, zoomed.x), 0f)
        assertEquals(-40f, zoomed.offset(200f, 100f, zoomed.y), 0f)
    }
}
