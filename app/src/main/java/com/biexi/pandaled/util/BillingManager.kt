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
import com.biexi.pandaled.BuildConfig
import com.biexi.pandaled.PandaLedApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ═══════════════════════════════════════════════════════════
//  ISubscribeManager – subscribe 接口
// ═══════════════════════════════════════════════════════════

interface ISubscribeManager {
    val isSubscribed: StateFlow<Boolean>
    val subscriptionPrice: StateFlow<String?>
    val restoreError: StateFlow<Int?>
    val restoreCounter: StateFlow<Int>

    fun initialize()
    fun querySubscriptionStatus()
    fun queryProductDetails()
    fun restoreSubscription()
    fun launchSubscription(activity: Activity): BillingResult?
    fun acknowledgePurchase(): BillingResult?
}

// ═══════════════════════════════════════════════════════════
//  DebugSubscribeManager – 调试用，模拟所有行为
// ═══════════════════════════════════════════════════════════

class DebugSubscribeManager : ISubscribeManager {

    companion object {
        private const val TAG = "DebugSubscribe"
    }

    // ═════════════════════════════════════════════════════════
    //  调试开关：改这两个值来模拟不同场景（影响所有方法）
    // ═════════════════════════════════════════════════════════
    /*
     * 生产环境可能的状态：
     *
     * ── mockResponseCode（BillingResponseCode）──
     *   影响 querySubscriptionStatus / launchSubscription / restoreSubscription
     *   OK (0)                   → 查询/购买成功 ✅
     *   ITEM_ALREADY_OWNED (7)   → 已拥有 ✅（等同于 OK）
     *   USER_CANCELED (1)        → 用户取消 ❌
     *   SERVICE_UNAVAILABLE (2)  → 服务不可用 ❌
     *   BILLING_UNAVAILABLE (3)  → 计费不可用 ❌
     *   ITEM_UNAVAILABLE (4)     → 商品不可用 ❌
     *   DEVELOPER_ERROR (5)      → 开发者错误 ❌
     *   ERROR (6)                → 一般错误 ❌
     *   ITEM_NOT_OWNED (8)       → 未拥有 ❌
     *
     * ── mockHasActiveSubscription（仅 responseCode == OK 时生效）──
     *   ITEM_ALREADY_OWNED 时忽略此值，直接设为已订阅
     *   querySubscriptionStatus 时如果 OK，用这个值设置 _isSubscribed
     *   true  → 已订阅 (ACTIVE)
     *   false → 未订阅 (INACTIVE)
     *   常见为 false 的原因：
     *     - 从未购买过
     *     - 订阅已过期
     *     - purchaseState == PENDING（待定中）
     *     - 购买未确认（!isAcknowledged）
     *     - purchaseList 为空
     *     - purchase.products 不包含 premium_monthly
     *
     * ── billingClient 状态（生产环境）──
     *   ::billingClient.isInitialized == false → 直接 return，_isSubscribed 不变
     *
     * ── mockAcknowledgeSucceeds ──
     *   模拟 acknowledgePurchase() 的返回值
     *   true  → 确认成功，触发 querySubscriptionStatus()
     *   false → 确认失败，返回 ERROR
     */
    private val mockResponseCode = BillingClient.BillingResponseCode.OK
    private val mockHasActiveSubscription = false
    private val mockAcknowledgeSucceeds = true
    // ═════════════════════════════════════════════════════════

    private val _isSubscribed = MutableStateFlow(false)
    override val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    private val _subscriptionPrice = MutableStateFlow<String?>(null)
    override val subscriptionPrice: StateFlow<String?> = _subscriptionPrice.asStateFlow()

    private val _restoreError = MutableStateFlow<Int?>(null)
    override val restoreError: StateFlow<Int?> = _restoreError.asStateFlow()

    private val _restoreCounter = MutableStateFlow(0)
    override val restoreCounter: StateFlow<Int> = _restoreCounter.asStateFlow()

    private fun isMockSuccess(): Boolean =
        mockResponseCode == BillingClient.BillingResponseCode.OK ||
        mockResponseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED

    override fun initialize() {
        Log.d(TAG, "Debug mode – querying mock subscription status")
        querySubscriptionStatus()
    }

    override fun querySubscriptionStatus() {
        if (isMockSuccess()) {
            // ITEM_ALREADY_OWNED → always subscribed; OK → controlled by flag
            val subscribed = mockResponseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED
                || mockHasActiveSubscription
            _isSubscribed.value = subscribed
            Log.d(TAG, "querySubscriptionStatus → response=$mockResponseCode, subscribed=$subscribed")
        } else {
            _isSubscribed.value = false
            Log.d(TAG, "querySubscriptionStatus → response=$mockResponseCode, subscribed=false")
        }
    }

    override fun queryProductDetails() {
        _subscriptionPrice.value = "¥8.88"
        Log.d(TAG, "queryProductDetails → price=${_subscriptionPrice.value}")
    }

    override fun restoreSubscription() {
        _restoreCounter.value++
        if (isMockSuccess()) {
            _isSubscribed.value = true
            _restoreError.value = null
            Log.d(TAG, "restoreSubscription → response=$mockResponseCode, subscribed=true")
        } else {
            _restoreError.value = BillingManager.RESTORE_ERROR_NOT_OWNED
            Log.d(TAG, "restoreSubscription → response=$mockResponseCode, restore failed")
        }
    }

    override fun launchSubscription(activity: Activity): BillingResult? {
        if (isMockSuccess()) {
            Log.d(TAG, "launchSubscription → response=$mockResponseCode, flow launched")
            // Simulate PurchasesUpdatedListener → handlePurchase → acknowledge
            acknowledgePurchase()
            return BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.OK)
                .build()
        } else {
            Log.d(TAG, "launchSubscription → response=$mockResponseCode, purchase failed")
            return BillingResult.newBuilder()
                .setResponseCode(mockResponseCode)
                .build()
        }
    }

    // Simulates PurchasesUpdatedListener → handlePurchase → acknowledge → query
    override fun acknowledgePurchase(): BillingResult? {
        if (mockAcknowledgeSucceeds) {
            _isSubscribed.value = true
            Log.d(TAG, "acknowledgePurchase → OK, subscribed=true")
            return BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.OK)
                .build()
        } else {
            Log.d(TAG, "acknowledgePurchase → failed")
            return BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.ERROR)
                .build()
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  ProductionSubscribeManager – 正式 Google Play Billing
// ═══════════════════════════════════════════════════════════

class ProductionSubscribeManager : ISubscribeManager {

    companion object {
        private const val TAG = "BillingManager"
        const val PRODUCT_ID = "premium_monthly"
        private const val BASE_PLAN_ID = "monthly-autorenew"
    }

    private lateinit var billingClient: BillingClient

    private val _isSubscribed = MutableStateFlow(false)
    override val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    private val _subscriptionPrice = MutableStateFlow<String?>(null)
    override val subscriptionPrice: StateFlow<String?> = _subscriptionPrice.asStateFlow()

    private var subscriptionProductDetails: ProductDetails? = null
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

    private val _restoreError = MutableStateFlow<Int?>(null)
    override val restoreError: StateFlow<Int?> = _restoreError.asStateFlow()

    private val _restoreCounter = MutableStateFlow(0)
    override val restoreCounter: StateFlow<Int> = _restoreCounter.asStateFlow()

    override fun initialize() {
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

    override fun querySubscriptionStatus() {
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

    override fun queryProductDetails() {
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

    override fun restoreSubscription() {
        if (!::billingClient.isInitialized) {
            _restoreError.value = BillingManager.RESTORE_ERROR_GENERIC
            _restoreCounter.value++
            return
        }
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
                _restoreError.value = if (active) null else BillingManager.RESTORE_ERROR_NOT_OWNED
                Log.d(TAG, "Restore: ${if (active) "ACTIVE" else "NOT OWNED"}")
            } else {
                _restoreError.value = BillingManager.RESTORE_ERROR_GENERIC
                Log.w(TAG, "Restore query failed: ${billingResult.debugMessage}")
            }
            _restoreCounter.value++
        }
    }

    override fun launchSubscription(activity: Activity): BillingResult? {
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

    override fun acknowledgePurchase(): BillingResult? {
        // Handled internally by PurchasesUpdatedListener → handlePurchase()
        // This method is exposed for interface completeness / debug mock symmetry
        querySubscriptionStatus()
        return BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.OK)
            .build()
    }

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

// ═══════════════════════════════════════════════════════════
//  BillingManager – 单例门面 (Facade)
//  BuildConfig.DEBUG → DebugSubscribeManager
//  !BuildConfig.DEBUG  → ProductionSubscribeManager
// ═══════════════════════════════════════════════════════════

object BillingManager {

    /** BillingResponseCode.OK 的简写，方便外部判断 */
    const val BILLING_OK = BillingClient.BillingResponseCode.OK

    /** restoreError 错误码：未拥有订阅（对应生产环境 ITEM_NOT_OWNED = 8） */
    const val RESTORE_ERROR_NOT_OWNED = 8

    /** restoreError 错误码：通用恢复失败 */
    const val RESTORE_ERROR_GENERIC = -1

    private val instance: ISubscribeManager by lazy {
        if (BuildConfig.DEBUG) {
            DebugSubscribeManager()
        } else {
            ProductionSubscribeManager()
        }
    }

    val isSubscribed: StateFlow<Boolean>
        get() = instance.isSubscribed

    val subscriptionPrice: StateFlow<String?>
        get() = instance.subscriptionPrice

    val restoreError: StateFlow<Int?>
        get() = instance.restoreError

    val restoreCounter: StateFlow<Int>
        get() = instance.restoreCounter

    fun initialize() = instance.initialize()

    fun querySubscriptionStatus() = instance.querySubscriptionStatus()

    fun queryProductDetails() = instance.queryProductDetails()

    fun restoreSubscription() = instance.restoreSubscription()

    fun launchSubscription(activity: Activity): BillingResult? =
        instance.launchSubscription(activity)

    fun acknowledgePurchase(): BillingResult? =
        instance.acknowledgePurchase()
}
