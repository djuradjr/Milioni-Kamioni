package com.example.stayfree.data.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature

class PurchaseVerifierTest {

    private val play = newKeyPair()
    private val json = """{"productId":"premium","purchaseState":0}"""

    @Test
    fun `purchase signed by Play verifies`() {
        assertTrue(PurchaseVerifier.verify(play.public.encoded, json, sign(play, json)))
    }

    @Test
    fun `tampered purchase JSON is rejected`() {
        val signature = sign(play, json)
        val forged = json.replace("\"purchaseState\":0", "\"purchaseState\":1")
        assertFalse(PurchaseVerifier.verify(play.public.encoded, forged, signature))
    }

    @Test
    fun `signature from another key is rejected`() {
        assertFalse(PurchaseVerifier.verify(play.public.encoded, json, sign(newKeyPair(), json)))
    }

    @Test
    fun `missing or garbage key fails closed`() {
        val signature = sign(play, json)
        assertFalse(PurchaseVerifier.verify(ByteArray(0), json, signature))
        assertFalse(PurchaseVerifier.verify(byteArrayOf(1, 2, 3), json, signature))
    }

    @Test
    fun `garbage signature fails closed`() {
        assertFalse(PurchaseVerifier.verify(play.public.encoded, json, ByteArray(0)))
        assertFalse(PurchaseVerifier.verify(play.public.encoded, json, byteArrayOf(9, 9, 9)))
    }

    private fun newKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun sign(keys: KeyPair, data: String): ByteArray =
        Signature.getInstance("SHA1withRSA").run {
            initSign(keys.private)
            update(data.toByteArray(Charsets.UTF_8))
            sign()
        }
}
