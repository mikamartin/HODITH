package com.secondmonday.hodith.ui.share

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareDisplayNameTest {
    @Test
    fun untouchedField_carriesTheCaseName() {
        assertEquals("Coffee", shareDisplayName(typed = null, caseName = "Coffee"))
    }

    @Test
    fun typedName_isUsedOnTheCard() {
        assertEquals("Sam's log", shareDisplayName(typed = "Sam's log", caseName = "Coffee"))
    }

    @Test
    fun blankOrWhitespaceField_fallsBackToTheCaseName() {
        assertEquals("Coffee", shareDisplayName(typed = "", caseName = "Coffee"))
        assertEquals("Coffee", shareDisplayName(typed = "   ", caseName = "Coffee"))
    }
}
