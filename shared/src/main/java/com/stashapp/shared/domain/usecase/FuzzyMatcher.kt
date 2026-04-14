package com.stashapp.shared.domain.usecase

import java.util.Locale
import kotlin.math.max

object FuzzyMatcher {

    /**
     * Calculates the Levenshtein distance between two strings, returning a similarity
     * score between 0.0 and 1.0 (where 1.0 is a perfect match).
     */
    fun levenshteinSimilarity(a: String, b: String): Double {
        val s1 = a.lowercase(Locale.ROOT).trim()
        val s2 = b.lowercase(Locale.ROOT).trim()

        if (s1.isEmpty() && s2.isEmpty()) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val maxLen = max(s1.length, s2.length)
        val distance = levenshtein(s1, s2)
        return 1.0 - (distance.toDouble() / maxLen.toDouble())
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length

        var cost = Array(lhsLength) { it }
        var newCost = Array(lhsLength) { 0 }

        for (i in 1 until rhsLength) {
            newCost[0] = i

            for (j in 1 until lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1

                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1

                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }

            val swap = cost
            cost = newCost
            newCost = swap
        }

        return cost[lhsLength - 1]
    }

    /**
     * Calculates the Jaccard similarity based on token overlap.
     * Splits strings into words and compares intersecting words.
     * Useful for order-independent matches ("Milk Halfvol" vs "Halfvolle Milk").
     */
    fun tokenOverlap(a: String, b: String): Double {
        val tokensA = a.lowercase(Locale.ROOT).split(Regex("""\W+""")).filter { it.isNotBlank() }.toSet()
        val tokensB = b.lowercase(Locale.ROOT).split(Regex("""\W+""")).filter { it.isNotBlank() }.toSet()

        if (tokensA.isEmpty() && tokensB.isEmpty()) return 1.0
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0

        val intersection = tokensA.intersect(tokensB).size
        val union = tokensA.union(tokensB).size

        return intersection.toDouble() / union.toDouble()
    }
}
