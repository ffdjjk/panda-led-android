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
    fun loadAndShow(activity: Activity, onDismissed: () -> Unit, onNetworkError: () -> Unit, onAdStarted: () -> Unit = {}) {
        if (!DebugConfig.enableAds()) {
            android.os.Handler(activity.mainLooper).postDelayed({
                onDismissed()
            }, 1500)
            return
        }
        if (!initialized) {
            com.google.android.gms.ads.MobileAds.initialize(activity) {
                initialized = true
                loadInternal(activity, onDismissed, onNetworkError, onAdStarted)
            }
        } else {
            loadInternal(activity, onDismissed, onNetworkError, onAdStarted)
        }
    }

    private fun loadInternal(activity: Activity, onDismissed: () -> Unit, onNetworkError: () -> Unit, onAdStarted: () -> Unit) {
        InterstitialAd.load(
            activity,
            INTERSTITIAL_UNIT_ID,
            com.google.android.gms.ads.AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            onDismissed()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                            if (error.code == 2) {
                                onNetworkError()
                            } else {
                                loadInternal(activity, onDismissed, onNetworkError, onAdStarted)
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
                    if (error.code == 2) {
                        onNetworkError()
                    } else {
                        loadInternal(activity, onDismissed, onNetworkError, onAdStarted)
                    }
                }
            }
        )
    }
}
