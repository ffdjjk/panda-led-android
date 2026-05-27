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
        Log.d("PandaFlow", "[AdManager] loadAndShow 调用, enableAds=${DebugConfig.enableAds()}, initialized=$initialized")
        if (!DebugConfig.enableAds()) {
            Log.d("PandaFlow", "[AdManager] 广告已禁用, 1500ms后触发fallback onDismissed")
            android.os.Handler(activity.mainLooper).postDelayed({
                Log.d("PandaFlow", "[AdManager] fallback onDismissed 触发")
                onDismissed()
            }, 1500)
            return
        }
        if (!initialized) {
            Log.d("PandaFlow", "[AdManager] MobileAds未初始化, 开始初始化...")
            com.google.android.gms.ads.MobileAds.initialize(activity) {
                initialized = true
                Log.d("PandaFlow", "[AdManager] MobileAds初始化完成, 进入loadInternal")
                loadInternal(activity, onDismissed, onNetworkError)
            }
        } else {
            Log.d("PandaFlow", "[AdManager] MobileAds已初始化, 直接进入loadInternal")
            loadInternal(activity, onDismissed, onNetworkError)
        }
    }

    private fun loadInternal(activity: Activity, onDismissed: () -> Unit, onNetworkError: () -> Unit) {
        Log.d("PandaFlow", "[AdManager] loadInternal → 加载插屏广告...")
        InterstitialAd.load(
            activity,
            INTERSTITIAL_UNIT_ID,
            com.google.android.gms.ads.AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d("PandaFlow", "[AdManager] onAdLoaded → 广告加载成功, 设置回调并show")
                    ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            Log.d("PandaFlow", "[AdManager] onAdDismissedFullScreenContent → 调用onDismissed")
                            onDismissed()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                            Log.w("PandaFlow", "[AdManager] onAdFailedToShow → code=${error.code}, msg=${error.message}")
                            if (error.code == 2) {
                                Log.w("PandaFlow", "[AdManager] 展示失败(网络错误code=2), 调用onNetworkError")
                                onNetworkError()
                            } else {
                                Log.w("PandaFlow", "[AdManager] 展示失败(非网络错误), 重试loadInternal")
                                loadInternal(activity, onDismissed, onNetworkError)
                            }
                        }

                        override fun onAdShowedFullScreenContent() {
                            Log.d("PandaFlow", "[AdManager] onAdShowedFullScreenContent → 广告已展示")
                        }

                        override fun onAdImpression() {
                            Log.d(TAG, "onAdImpression() - ad impression recorded")
                        }
                    }
                    Log.d(TAG, "Calling ad.show()...")
                    ad.show(activity)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w("PandaFlow", "[AdManager] onAdFailedToLoad → code=${error.code}, msg=${error.message}")
                    if (error.code == 2) {
                        Log.w("PandaFlow", "[AdManager] 加载失败(网络错误code=2), 调用onNetworkError")
                        onNetworkError()
                    } else {
                        Log.w("PandaFlow", "[AdManager] 加载失败(非网络错误), 重试loadInternal")
                        loadInternal(activity, onDismissed, onNetworkError)
                    }
                }
            }
        )
    }
}
