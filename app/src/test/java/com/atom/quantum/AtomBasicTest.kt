package com.atom.quantum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AtomBasicTest {
    @Test fun defaultBuildHasNoSecretToken() {
        assertTrue("Never bake API tokens into a distributable app", "HF_TOKEN" !in BuildConfig.API_BASE_URL)
    }

    @Test fun chatModelStoresUserRole() {
        val message = MessageEntity("1", "chat", "user", "مرحبا", 1L)
        assertEquals("user", message.role)
        assertEquals("مرحبا", message.content)
    }
}
