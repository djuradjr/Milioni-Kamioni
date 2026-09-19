package com.example.stayfree.data.billing

import android.app.Activity
import android.content.Context
import android.util.Base64
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.stayfree.data.local.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    // Needed to launch the purchase sheet for a plan picked from the last loadPlans().
    @Volatile private var premiumDetails: ProductDetails? = null

    /** Current premium state for UI gates; follows every refresh. */
    val isPremium: StateFlow<Boolean> =
        prefs.premiumActive.stateIn(scope, SharingStarted.Eagerly, false)

    private val _purchasePending = MutableStateFlow(false)
    /** A purchase paid by a slow method (cash, bank transfer) that Play hasn't confirmed yet. */
    val purchasePending: StateFlow<Boolean> = _purchasePending

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

    /** Null when Play can't list the plans (no Play Store, signed out, products not live). */
    suspend fun loadPlans(): List<PremiumPlan>? = withContext(Dispatchers.IO) {
        DebugPremium.fakePlans(context) ?: withTimeoutOrNull(PLAY_TIMEOUT_MS) { queryPlans() }
    }

    /** Main thread only. False when the Play purchase sheet could not open. */
    fun launchPurchase(activity: Activity, plan: PremiumPlan): Boolean {
        if (DebugPremium.fakePurchase(context)) {
            refresh()
            return true
        }
        val details = premiumDetails ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(plan.offerToken)
                        .build()
                )
            )
            .build()
        val code = billingClient.launchBillingFlow(activity, params).responseCode
        if (code != BillingResponseCode.OK) Log.w(TAG, "Purchase sheet failed: $code")
        return code == BillingResponseCode.OK
    }

    /** Asks Play right now; true when this Google account already owns premium. */
    suspend fun restore(): Boolean = withContext(Dispatchers.IO) {
        refreshNow()
        prefs.premiumActive.first()
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
        _purchasePending.value = purchases.any {
            PREMIUM_PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PENDING
        }
        val genuine = purchased.filter(::isSignedByPlay)
        if (genuine.size < purchased.size) {
            Log.w(TAG, "Rejected ${purchased.size - genuine.size} purchase(s) with a bad signature")
        }
        genuine.filterNot { it.isAcknowledged }.forEach { acknowledge(it) }
        return genuine.isNotEmpty()
    }

    private suspend fun queryPlans(): List<PremiumPlan>? {
        if (!connect()) return null
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()
        val (result, details) = suspendCancellableCoroutine { cont ->
            billingClient.queryProductDetailsAsync(params) { result, found ->
                if (cont.isActive) cont.resume(result to found.productDetailsList.firstOrNull())
            }
        }
        if (result.responseCode != BillingResponseCode.OK || details == null) {
            Log.w(TAG, "No plans: code=${result.responseCode}, product found=${details != null}")
            return null
        }
        premiumDetails = details
        return details.subscriptionOfferDetails.orEmpty()
            .groupBy { it.basePlanId }
            .mapNotNull { (_, offers) -> toPlan(offers) }
            .sortedBy { it.months }
            .ifEmpty { null }
    }

    // Play returns only the offers this user is eligible for, so a free-phase offer here is a
    // trial they can actually take; without one the plain base plan is sold.
    private fun toPlan(offers: List<ProductDetails.SubscriptionOfferDetails>): PremiumPlan? {
        val offer = offers.firstOrNull { o -> o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } }
            ?: offers.firstOrNull { it.offerId == null }
            ?: return null
        val phases = offer.pricingPhases.pricingPhaseList
        val recurring = phases.lastOrNull() ?: return null
        val months = PremiumPlan.months(recurring.billingPeriod) ?: return null
        val trialDays = phases.firstOrNull { it.priceAmountMicros == 0L }
            ?.let { PremiumPlan.days(it.billingPeriod) } ?: 0
        return PremiumPlan(
            months = months,
            price = recurring.formattedPrice,
            priceMicros = recurring.priceAmountMicros,
            currencyCode = recurring.priceCurrencyCode,
            trialDays = trialDays,
            offerToken = offer.offerToken
        )
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
