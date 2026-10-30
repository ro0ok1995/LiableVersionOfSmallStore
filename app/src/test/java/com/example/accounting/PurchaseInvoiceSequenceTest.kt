package com.example.accounting

import com.example.data.repository.PurchasesRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class PurchaseInvoiceSequenceTest {
    @Test
    fun migrationRepairNumbersDoNotResetNumericSequence() {
        assertEquals(
            42L,
            PurchasesRepository.nextNumericSequence(listOf("PUR-000041", "PUR-FIX-pur_legacy_1", "PUR-000009"))
        )
    }

    @Test
    fun customAndMalformedNumbersAreIgnored() {
        assertEquals(
            1L,
            PurchasesRepository.nextNumericSequence(listOf("PUR-FIX-abc", "PUR-ABC", "PUR-12X", "PUR-"))
        )
    }

    @Test
    fun sequenceUsesMaximumRatherThanLexicographicLastValue() {
        assertEquals(
            1000000L,
            PurchasesRepository.nextNumericSequence(listOf("PUR-999999", "PUR-100000", "PUR-FIX-z"))
        )
    }
}
