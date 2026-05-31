package com.biexi.pandaled.util

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import com.biexi.pandaled.PandaLedApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BillingManager {

    private const val TAG = "BillingManager"

    const val PRODUCT_ID = "premium_monthly"
    private const val BASE_PLAN_ID = "monthly-autorenew"

    lateinit var billingClient: BillingClient
        private set

    /** Whether the user currently holds an active subscription. */
    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    /** Formatted subscription price (e.g. "¥35.00" or "$4.99"), or null if not yet loaded. */
    private val _subscriptionPrice = MutableStateFlow<String?>(null)
    val subscriptionPrice: StateFlow<String?> = _subscriptionPrice.asStateFlow()

    /** Product details for the subscription, available after successful connection. */
    private var subscriptionProductDetails: ProductDetails? = null

    /** Offer token for the base plan we want to sell. */
    private var basePlanOfferToken: String? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            Log.d(TAG, "Purchase updated: ${purchases.size} purchases")
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    handlePurchase(purchase)
                }
            }
        } else {
            Log.d(TAG, "Purchase update: ${billingResult.debugMessage}")
        }
    }

    fun initialize() {
        if (!DebugConfig.enableBilling()) {
            Log.d(TAG, "Billing disabled via debug config, skipping initialization")
            return
        }

        billingClient = BillingClient.newBuilder(PandaLedApp.instance)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "BillingClient connected successfully")
                    querySubscriptionStatus()
                    queryProductDetails()
                } else {
                    Log.w(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected, attempting reconnect...")
                billingClient.startConnection(this)
            }
        })
    }

    // ─── Query active subscription ───────────────────────

    fun querySubscriptionStatus() {
        if (!::billingClient.isInitialized) return

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchaseList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val active = purchaseList.any { purchase ->
                    purchase.products.contains(PRODUCT_ID) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        purchase.isAcknowledged
                }
                _isSubscribed.value = active
                Log.d(TAG, "Subscription status: ${if (active) "ACTIVE" else "INACTIVE"}")
            }
        }
    }

    // ─── Query product details ───────────────────────────

    fun queryProductDetails() {
        if (!::billingClient.isInitialized) return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(
            params,
            object : ProductDetailsResponseListener {
                override fun onProductDetailsResponse(
                    billingResult: BillingResult,
                    result: QueryProductDetailsResult
                ) {
                    val productDetailsList = result.productDetailsList
                    Log.d(TAG, "queryProductDetails response: code=${billingResult.responseCode}, listSize=${productDetailsList?.size}")
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList != null) {
                        subscriptionProductDetails = if (productDetailsList.isNotEmpty()) productDetailsList[0] else null
                        val offers = subscriptionProductDetails?.subscriptionOfferDetails
                        Log.d(TAG, "subscriptionOfferDetails size=${offers?.size}, basePlanIds=${offers?.map { it.basePlanId }}")
                        val offer = offers?.find { it.basePlanId == BASE_PLAN_ID }
                        basePlanOfferToken = offer?.offerToken
                        // Extract formatted price from the first pricing phase
                        val phases = offer?.pricingPhases?.pricingPhaseList
                        _subscriptionPrice.value = phases?.firstOrNull()?.formattedPrice
                        Log.d(TAG, "Product details loaded, offerToken=${if (basePlanOfferToken != null) "found" else "NOT FOUND"}, phaseCount=${phases?.size}, price=${_subscriptionPrice.value}")
                    } else {
                        Log.w(TAG, "queryProductDetails failed: ${billingResult.debugMessage}")
                    }
                }
            }
        )
    }

    // ─── Launch subscription purchase ────────────────────

    /**
     * Launch the Google Play billing flow for the monthly subscription.
     * @return BillingResult from launchBillingFlow, or null if product details not ready.
     */
    fun launchSubscription(activity: Activity): BillingResult? {
        val productDetails = subscriptionProductDetails
        val offerToken = basePlanOfferToken

        if (productDetails == null || offerToken == null) {
            Log.w(TAG, "Product details or offer token not ready, re-querying...")
            queryProductDetails()
            return null
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        return billingClient.launchBillingFlow(activity, flowParams)
    }

    // ─── Handle successful purchase ──────────────────────

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            billingClient.acknowledgePurchase(
                com.android.billingclient.api.AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            ) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Purchase acknowledged successfully")
                    querySubscriptionStatus()
                } else {
                    Log.w(TAG, "Acknowledge failed: ${billingResult.debugMessage}")
                }
            }
        } else {
            querySubscriptionStatus()
        }
    }
}
