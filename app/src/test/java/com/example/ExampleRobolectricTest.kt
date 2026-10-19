package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.brainpal.quitscrolling.core.accessibility.ScrollSessionState
import com.brainpal.quitscrolling.data.model.SocialPlatform
import com.brainpal.quitscrolling.ui.components.BrainRotStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Likhon", appName)
    }

    @Test
    fun `brain rot stage transitions match 4-stage thresholds`() {
        assertEquals(BrainRotStage.FRESH, BrainRotStage.fromCount(0))
        assertEquals(BrainRotStage.FRESH, BrainRotStage.fromCount(49))
        assertEquals(BrainRotStage.DRAINING, BrainRotStage.fromCount(50))
        assertEquals(BrainRotStage.DRAINING, BrainRotStage.fromCount(99))
        assertEquals(BrainRotStage.ROTTING, BrainRotStage.fromCount(100))
        assertEquals(BrainRotStage.ROTTING, BrainRotStage.fromCount(199))
        assertEquals(BrainRotStage.DEAD_ROT, BrainRotStage.fromCount(200))
        assertEquals(BrainRotStage.DEAD_ROT, BrainRotStage.fromCount(350))
    }

    @Test
    fun `social platform lookup maps target packages accurately including X variants`() {
        assertEquals(SocialPlatform.INSTAGRAM, SocialPlatform.fromPackageName("com.instagram.android"))
        assertEquals(SocialPlatform.INSTAGRAM, SocialPlatform.fromPackageName("com.instagram.lite"))
        assertEquals(SocialPlatform.YOUTUBE, SocialPlatform.fromPackageName("com.google.android.youtube"))
        assertEquals(SocialPlatform.FACEBOOK, SocialPlatform.fromPackageName("com.facebook.katana"))
        assertEquals(SocialPlatform.FACEBOOK, SocialPlatform.fromPackageName("com.facebook.lite"))
        assertEquals(SocialPlatform.X_TWITTER, SocialPlatform.fromPackageName("com.twitter.android"))
        assertEquals(SocialPlatform.X_TWITTER, SocialPlatform.fromPackageName("com.twitter.android.lite"))
        assertEquals(SocialPlatform.X_TWITTER, SocialPlatform.fromPackageName("com.x.android"))
    }

    @Test
    fun `scroll session state tracks seen fingerprints and resets cleanly`() {
        val state = ScrollSessionState()
        state.rememberFingerprint(101)
        state.rememberFingerprint(202)
        assertTrue(101 in state.seenFingerprints)
        assertTrue(202 in state.seenFingerprints)

        state.resetSession()
        assertFalse(state.isInReelSession)
        assertFalse(state.hasCountedInitialReel)
        assertTrue(state.seenFingerprints.isEmpty())
    }
}
