package com.secondmonday.hodith.ui.share

import com.secondmonday.hodith.ui.theme.ShareCardSkin
import org.junit.Assert.assertEquals
import org.junit.Test

class KickerTextTest {
    @Test
    fun `kickerText capitalises the range on the Intense skin only`() {
        assertEquals("MAR 2 – OCT 5", kickerText("Mar 2 – Oct 5", ShareCardSkin.INTENSE))
        assertEquals("Mar 2 – Oct 5", kickerText("Mar 2 – Oct 5", ShareCardSkin.PLAIN))
        assertEquals("Mar 2 – Oct 5", kickerText("Mar 2 – Oct 5", ShareCardSkin.BRIGHT))
    }
}
