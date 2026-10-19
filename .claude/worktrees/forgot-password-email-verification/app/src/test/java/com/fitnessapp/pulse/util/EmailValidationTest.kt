package com.fitnessapp.pulse.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailValidationTest {
    @Test
    fun `valid email is accepted`() {
        assertTrue(isValidEmail("student@example.com"))
    }

    @Test
    fun `email missing at sign is rejected`() {
        assertFalse(isValidEmail("studentexample.com"))
    }

    @Test
    fun `email missing domain dot is rejected`() {
        assertFalse(isValidEmail("student@examplecom"))
    }

    @Test
    fun `blank email is rejected`() {
        assertFalse(isValidEmail("  "))
    }

    @Test
    fun `email with surrounding whitespace is trimmed and accepted`() {
        assertTrue(isValidEmail("  student@example.com  "))
    }
}
