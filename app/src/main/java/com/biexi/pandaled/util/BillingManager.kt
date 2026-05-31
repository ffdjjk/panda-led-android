package com.biexi.pandaled.util

import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.PurchasesUpdatedListener
import com.biexi.pandaled.PandaLedApp

object BillingManager {

    private const val TAG = "BillingManager"

    lateinit var billingClient: BillingClient
        private set

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            Log.d(TAG, "Purchase updated: ${purchases.size} purchases")
            // TODO: Handle purchase verification & entitlement
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
                    // TODO: Query existing purchases / products after subscriptions are set up
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
}
