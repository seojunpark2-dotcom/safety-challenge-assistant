package kr.co.safetyassistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenOffCleanupTest {
    @Test fun recognizesOnlyIndividualCloseActions() {
        assertTrue(isRecentTaskCloseLabel(" Close "))
        assertTrue(isRecentTaskCloseLabel("앱 닫기"))
        assertFalse(isRecentTaskCloseLabel("Close all"))
        assertFalse(isRecentTaskCloseLabel("모두 닫기"))
        assertFalse(isRecentTaskCloseLabel(null))
    }
}
