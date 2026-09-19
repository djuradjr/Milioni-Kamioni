package com.example.stayfree.data.billing

import android.content.Context
import android.util.Base64
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryPurchasesParams
import com.example.stayfree.data.local.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * The only writer of the premium state. With no backend, Play is asked on app start, on
 * every resume and periodically while the process lives — the a11y service keeps it alive
 * for days, and a lapsed subscription must stop blocking without the user opening the app.
 * The verified result lands in [AppPreferences.premiumActive], which the app observes.
 */
@Singleton
class PremiumRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Mutex()

    @Volatile private var connected = false

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val licenseKey: ByteArray? by lazy { decodeBase64(LICENSE_KEY) }

    fun start() {
        scope.launch {
            while (isActive) {
                refreshNow()
                delay(PERIODIC_REFRESH_MS)
            }
        }
    }

    fun refresh() {
        scope.launch { refreshNow() }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        // Only carries the purchases just made; a full query keeps one code path.
        if (result.responseCode == BillingResponseCode.OK) refresh()
    }

    private suspend fun refreshNow() = refreshLock.withLock { queryAndPublish() }

    private suspend fun queryAndPublish() {
        // A Play call that never answers must not hold the lock for the life of the process.
        val active = DebugPremium.override(context)
            ?: withTimeoutOrNull(PLAY_TIMEOUT_MS) { queryPlay() }
            ?: return
        prefs.setPremiumActive(active)
        Log.i(TAG, "Premium refreshed: active=$active")
    }

    /** Null when Play could not answer: the cached state stays rather than revoking a payer. */
    private suspend fun queryPlay(): Boolean? {
        if (!connect()) return null
        val (result, purchases) = querySubscriptions()
        if (result.responseCode != BillingResponseCode.OK) {
            Log.w(TAG, "Purchase query failed: ${result.responseCode}")
            return null
        }
        val purchased = purchases.filter {
            PREMIUM_PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        val genuine = purchased.filter(::isSignedByPlay)
        if (genuine.size < purchased.size) {
            Log.w(TAG, "Rejected ${purchased.size - genuine.size} purchase(s) with a bad signature")
        }
        genuine.filterNot { it.isAcknowledged }.forEach { acknowledge(it) }
        return genuine.isNotEmpty()
    }

    private suspend fun connect(): Boolean {
        // After the first setup, auto-reconnection heals dropped connections on the next call.
        if (connected) return true
        return suspendCancellableCoroutine { cont ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    val ok = result.responseCode == BillingResponseCode.OK
                    if (ok) connected = true else Log.w(TAG, "Billing setup failed: ${result.responseCode}")
                    if (cont.isActive) cont.resume(ok)
                }

                override fun onBillingServiceDisconnected() = Unit
            })
        }
    }

    // Suspended (payment-hold) subscriptions are excluded by default — exactly the
    // "not entitled" case.
    private suspend fun querySubscriptions(): Pair<BillingResult, List<Purchase>> =
        suspendCancellableCoroutine { cont ->
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                if (cont.isActive) cont.resume(result to purchases)
            }
        }

    // Play refunds anything unacknowledged after 3 days; a failure retries on the next refresh.
    private suspend fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        val code = suspendCancellableCoroutine { cont ->
            billingClient.acknowledgePurchase(params) { result ->
                if (cont.isActive) cont.resume(result.responseCode)
            }
        }
        if (code != BillingResponseCode.OK) Log.w(TAG, "Acknowledge failed: $code")
    }

    private fun isSignedByPlay(purchase: Purchase): Boolean {
        val key = licenseKey ?: return false
        val signature = decodeBase64(purchase.signature) ?: return false
        return PurchaseVerifier.verify(key, purchase.originalJson, signature)
    }

    private fun decodeBase64(value: String): ByteArray? =
        if (value.isBlank()) null
        else try {
            Base64.decode(value, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            null
        }

    companion object {
        private const val TAG = "PremiumBilling"
        const val PREMIUM_PRODUCT_ID = "premium"
        private const val PERIODIC_REFRESH_MS = 6 * 60 * 60 * 1000L
        private const val PLAY_TIMEOUT_MS = 30_000L

        // Play Console → Monetize with Play → Monetization setup → Licensing. A public
        // key, safe to ship; a broken one makes every purchase fail verification.
        internal const val LICENSE_KEY =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA6re054AeCK8u8b1N/JHw" +
            "PQXIjEsyUeXY4fo2sLU4WIS2O/vyyihhNUA0AIvx3m38QKbnXPtrRlvM/Gi7CkX/" +
            "uS+MOg6t0nACtMvQn2Met4FxUQngYX7OwMHOHB7V8LYIg3wsDDCC7acvT94whGbf" +
            "HpFnHatcZhCJHsFKALQCnufnhzznvNh9FaieOzDl17J8UZaV567NOALHD4MfZMEo" +
            "PWDNW1+OyRiVEVgg0LPmFEqeoFn7dGLV/FxaL5eYyZtg0ak6F4zdpVclQmgcsnHQ" +
            "LviDEIdhLCde+gjWdOOjES8Tzq+5pOS6e0Hpa5bOXXPnXsXCYCViNkknSvIzwZOV" +
            "lwIDAQAB"
    }
}
