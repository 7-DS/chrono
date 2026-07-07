package com.chrono.ssh.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SecretRefPolicyTest {
    @Test
    fun acceptsGeneratedSecretRefs() {
        val ref = "secret-main-key-123e4567-e89b-12d3-a456-426614174000.v1"

        assertEquals(ref, SecretRefPolicy.requireValid(ref))
    }

    @Test
    fun rejectsPathsAndMalformedRefs() {
        assertThrows(IllegalArgumentException::class.java) {
            SecretRefPolicy.requireValid("../secret-main-key-123e4567-e89b-12d3-a456-426614174000.v1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SecretRefPolicy.requireValid("secret-main-key-123e4567-e89b-12d3-a456-426614174000.v1/other")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SecretRefPolicy.requireValid("secret-main-key.v1")
        }
    }
}
