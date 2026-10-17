package com.example.accounting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountingDecimalTest {
    @Test fun decimalMoneyAddsWithoutBinaryDrift() {
        assertEquals(10.23, AccountingDecimal.addMoney(10.0, 0.2, 0.03), 0.0)
    }
    @Test fun moneyUsesTwoDecimalHalfUpRounding() {
        assertEquals(150.25, AccountingDecimal.money(150.245), 0.0)
    }
    @Test fun parserAcceptsDecimalAndCommaSeparator() {
        assertEquals(150.25, AccountingDecimal.parseMoney("150,25").toDouble(), 0.0)
    }
    @Test fun rejectsNonFiniteMoney() {
        assertThrows(IllegalArgumentException::class.java) { AccountingDecimal.money(Double.NaN) }
    }
}
