package com.biexi.pandaled.util

import android.app.Activity
import android.os.Build
import android.util.Log
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdManager {

    private const val TAG = "AdManager"
    private const val INTERSTITIAL_UNIT_ID = "ca-app-pub-7768614948828316/2394673168"
    private var initialized = false
    private var currentRequestId = 0

    /**
     * Cancel the currently pending ad request. Call this when the user
     * navigates away from the screen that initiated the ad load, so the
     * ad won't pop up after the user has already left.
     */
    fun cancelCurrentAd() {
        currentRequestId++
        Log.d(TAG, "Ad request cancelled (new requestId=$currentRequestId)")
    }

    /** Load ad, then show it. Skips ad entirely if user is subscribed. Retries on non-network errors. */
    fun loadAndShow(activity: Activity, onDismissed: () -> Unit, onNetworkError: () -> Unit, onAdStarted: () -> Unit = {}) {
        if (!DebugConfig.enableAds() || BillingManager.isSubscribed.value) {
            android.os.Handler(activity.mainLooper).postDelayed({
                onDismissed()
            }, 1500)
            return
        }
        val requestId = ++currentRequestId
        if (!initialized) {
            com.google.android.gms.ads.MobileAds.initialize(activity) {
                initialized = true
                loadInternal(activity, requestId, onDismissed, onNetworkError, onAdStarted)
            }
        } else {
            loadInternal(activity, requestId, onDismissed, onNetworkError, onAdStarted)
        }
    }

    private fun loadInternal(activity: Activity, requestId: Int, onDismissed: () -> Unit, onNetworkError: () -> Unit, onAdStarted: () -> Unit) {
        InterstitialAd.load(
            activity,
            INTERSTITIAL_UNIT_ID,
            com.google.android.gms.ads.AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    // Abort if this request was cancelled (user navigated away)
                    if (requestId != currentRequestId) {
                        Log.d(TAG, "Ad loaded but request was cancelled, discarding")
                        return
                    }
                    // Abort if activity is no longer valid
                    if (activity.isFinishing || activity.isDestroyed) {
                        Log.d(TAG, "Ad loaded but activity is finishing/destroyed, discarding")
                        return
                    }
                    ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            onDismissed()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                            if (error.code == 2) {
                                onNetworkError()
                            } else {
                                loadInternal(activity, requestId, onDismissed, onNetworkError, onAdStarted)
                            }
                        }

                        override fun onAdShowedFullScreenContent() {
                            onAdStarted()
                        }

                        override fun onAdImpression() {
                            Log.d(TAG, "onAdImpression() - ad impression recorded")
                        }
                    }
                    Log.d(TAG, "Calling ad.show()...")
                    ad.show(activity)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    // Abort retry if request was cancelled
                    if (requestId != currentRequestId) {
                        Log.d(TAG, "Ad load failed but request was cancelled, discarding")
                        return
                    }
                    if (error.code == 2) {
                        onNetworkError()
                    } else {
                        loadInternal(activity, requestId, onDismissed, onNetworkError, onAdStarted)
                    }
                }
            }
        )
    }
}
