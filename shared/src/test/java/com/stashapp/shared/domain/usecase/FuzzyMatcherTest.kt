package com.stashapp.shared.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {

    @Test
    fun testLevenshteinSimilarity() {
        // Exact match
        assertEquals(1.0, FuzzyMatcher.levenshteinSimilarity("Apple", "apple"), 0.01)
        
        // One character different
        val sim1 = FuzzyMatcher.levenshteinSimilarity("Apple", "Appel")
        assertTrue(sim1 > 0.7)
        
        // Totally different
        val sim2 = FuzzyMatcher.levenshteinSimilarity("Apple", "Banana")
        assertTrue(sim2 < 0.3)
    }

    @Test
    fun testTokenOverlap() {
        // Exact overlap regardless of order
        assertEquals(1.0, FuzzyMatcher.tokenOverlap("Melk Halfvol", "Halfvol melk"), 0.01)
        
        // Partial overlap
        val overlap1 = FuzzyMatcher.tokenOverlap("JUMBO Melk Halfvol", "Melk Halfvol")
        assertEquals(0.66, overlap1, 0.05)
        
        // No overlap
        assertEquals(0.0, FuzzyMatcher.tokenOverlap("Apple", "Banana"), 0.01)
    }
}
