package net.yukh.xui.ui.screen.clients

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors the panel's isPostQuantumLink cases (3.10.0, #6730). */
class PostQuantumLinkTest {
    private val mlkemKey = "A".repeat(1580)
    private val x25519Key = "B".repeat(43)

    @Test
    fun mldsaVerifyKeyHidesTheQr() {
        assertTrue(isPostQuantumLink("vless://u@h:443?security=reality&pqv=${"C".repeat(2000)}#r"))
    }

    @Test
    fun mlkemVlessEncryptionHidesTheQr() {
        assertTrue(isPostQuantumLink("vless://u@h:443?encryption=mlkem768x25519plus.native.0rtt.$mlkemKey#r"))
    }

    @Test
    fun x25519EncryptionRealityHintAndRemarkKeepTheQr() {
        assertFalse(isPostQuantumLink("vless://u@h:443?encryption=mlkem768x25519plus.native.0rtt.$x25519Key#r"))
        assertFalse(isPostQuantumLink("vless://u@h:443?security=reality&support-x25519mlkem768=true&pqv=#r"))
        assertFalse(isPostQuantumLink("vless://u@h:443?encryption=none#mlkem768"))
        assertFalse(isPostQuantumLink("https://sub.example/sub/abc"))
    }
}
