package com.example.stayfree.data.billing

import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Offline proof that a purchase came from Google Play: the purchase JSON must carry
 * a valid signature from this app's Play licensing key. Takes raw bytes so it stays
 * testable on the JVM (android.util.Base64 is not).
 */
object PurchaseVerifier {

    fun verify(publicKeyDer: ByteArray, signedData: String, signature: ByteArray): Boolean = try {
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyDer))
        Signature.getInstance("SHA1withRSA").run {
            initVerify(key)
            update(signedData.toByteArray(Charsets.UTF_8))
            verify(signature)
        }
    } catch (e: GeneralSecurityException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }
}
