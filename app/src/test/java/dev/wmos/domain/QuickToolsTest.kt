package dev.wmos.domain

import dev.wmos.domain.quick.PasswordGenerator
import dev.wmos.domain.quick.SafeExpression
import dev.wmos.domain.quick.UnitConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickToolsTest {
    @Test
    fun `multiplication has priority over addition`() {
        assertEquals(6.0, SafeExpression.evaluate("2+2*2").getOrThrow(), 0.0001)
    }

    @Test
    fun `parentheses change the order`() {
        assertEquals(60.0, SafeExpression.evaluate("(120 + 30) * 2 / 5").getOrThrow(), 0.0001)
    }

    @Test
    fun `unary minus is supported`() {
        assertEquals(-5.0, SafeExpression.evaluate("-5").getOrThrow(), 0.0001)
        assertEquals(5.0, SafeExpression.evaluate("10 + -5").getOrThrow(), 0.0001)
    }

    @Test
    fun `division by zero is rejected`() {
        assertTrue(SafeExpression.evaluate("10/0").isFailure)
    }

    @Test
    fun `letters are rejected`() {
        assertTrue(SafeExpression.evaluate("abc").isFailure)
    }

    @Test
    fun `unbalanced parentheses are rejected`() {
        assertTrue(SafeExpression.evaluate("(1+2").isFailure)
    }

    @Test
    fun `comma decimal separator works`() {
        assertEquals(2.5, SafeExpression.evaluate("1,5+1").getOrThrow(), 0.0001)
    }

    @Test
    fun `password has the requested length and classes`() {
        val password = PasswordGenerator.generate(24, useDigits = true, useSymbols = true, useUpper = true)
        assertEquals(24, password.length)
        assertTrue(password.any { it.isDigit() })
        assertTrue(password.any { it.isUpperCase() })
        assertTrue(password.any { !it.isLetterOrDigit() })
    }

    @Test
    fun `two generated passwords differ`() {
        val first = PasswordGenerator.generate(32)
        val second = PasswordGenerator.generate(32)
        assertNotEquals(first, second)
    }

    @Test
    fun `weak options are reported as weaker`() {
        assertEquals("Слабый", PasswordGenerator.strength(8, false, false, false))
        assertEquals("Очень сильный", PasswordGenerator.strength(40, true, true, true))
    }

    @Test
    fun `length is clamped to a safe range`() {
        assertEquals(8, PasswordGenerator.generate(1).length)
        assertEquals(128, PasswordGenerator.generate(1000).length)
    }

    @Test
    fun `kilometres to metres`() {
        assertEquals(2500.0, UnitConverter.convert(UnitConverter.Kind.LENGTH, "km", "m", 2.5), 0.0001)
    }

    @Test
    fun `celsius to fahrenheit`() {
        assertEquals(212.0, UnitConverter.convert(UnitConverter.Kind.TEMPERATURE, "c", "f", 100.0), 0.0001)
    }

    @Test
    fun `celsius to kelvin`() {
        assertEquals(273.15, UnitConverter.convert(UnitConverter.Kind.TEMPERATURE, "c", "k", 0.0), 0.0001)
    }
}
