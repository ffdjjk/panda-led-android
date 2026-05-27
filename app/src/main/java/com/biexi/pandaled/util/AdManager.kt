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

    /** Load ad, then show it. Retries on non-network errors. */
    fun loadAndShow(activity: Activity, onDismissed: () -> Unit, onNetworkError: () -> Unit) {
        Log.d(TAG, "loadAndShow() called, SDK=${Build.VERSION.SDK_INT}, enableAds=${DebugConfig.enableAds()}, initialized=$initialized")
        if (!DebugConfig.enableAds()) {
            Log.d(TAG, "Ads disabled, scheduling fallback onDismissed in 1500ms")
            android.os.Handler(activity.mainLooper).postDelayed({
                Log.d(TAG, "Fallback onDismissed triggered (ads disabled)")
                onDismissed()
            }, 1500)
            return
        }
        if (!initialized) {
            Log.d(TAG, "MobileAds not initialized, calling MobileAds.initialize()...")
            com.google.android.gms.ads.MobileAds.initialize(activity) {
                initialized = true
                Log.d(TAG, "MobileAds.initialize() completed, proceeding to loadInternal()")
                loadInternal(activity, onDismissed, onNetworkError)
            }
        } else {
            Log.d(TAG, "MobileAds already initialized, proceeding to loadInternal()")
            loadInternal(activity, onDismissed, onNetworkError)
        }
    }

    private fun loadInternal(activity: Activity, onDismissed: () -> Unit, onNetworkError: () -> Unit) {
        Log.d(TAG, "loadInternal() called, loading interstitial ad...")
        InterstitialAd.load(
            activity,
            INTERSTITIAL_UNIT_ID,
            com.google.android.gms.ads.AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "onAdLoaded() - ad loaded successfully, setting callback and showing...")
                    ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            Log.d(TAG, "onAdDismissedFullScreenContent() - calling onDismissed")
                            onDismissed()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                            Log.w(TAG, "onAdFailedToShowFullScreenContent() - code=${error.code}, message=${error.message}, domain=${error.domain}")
                            if (error.code == 2) {
                                Log.w(TAG, "Show failed with network error (code=2), calling onNetworkError")
                                onNetworkError()
                            } else {
                                Log.w(TAG, "Show failed with non-network error, retrying loadInternal()")
                                loadInternal(activity, onDismissed, onNetworkError)
                            }
                        }

                        override fun onAdShowedFullScreenContent() {
                            Log.d(TAG, "onAdShowedFullScreenContent() - ad is now visible")
                        }

                        override fun onAdImpression() {
                            Log.d(TAG, "onAdImpression() - ad impression recorded")
                        }
                    }
                    Log.d(TAG, "Calling ad.show()...")
                    ad.show(activity)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "onAdFailedToLoad() - code=${error.code}, message=${error.message}, domain=${error.domain}, responseInfo=${error.responseInfo}")
                    if (error.code == 2) {
                        Log.w(TAG, "Load failed with network error (code=2), calling onNetworkError")
                        onNetworkError()
                    } else {
                        Log.w(TAG, "Load failed with non-network error, retrying loadInternal()")
                        loadInternal(activity, onDismissed, onNetworkError)
                    }
                }
            }
        )
    }
}
