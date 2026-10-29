package com.captaink.tvlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class MainActivity : Activity() {
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.setBackgroundColor(Color.BLACK)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(Bridge(), "Android")
        setContentView(web)
        hideBars()
        web.loadUrl("file:///android_asset/index.html")
    }

    @Suppress("DEPRECATION")
    private fun hideBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    override fun onResume() {
        super.onResume()
        hideBars()
        web.evaluateJavascript("window.refreshAll && refreshAll()", null)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        web.evaluateJavascript("window.goHome && goHome()", null)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // A home screen should not close itself.
    }

    inner class Bridge {
        @JavascriptInterface
        fun getApps(): String {
            val pm = packageManager
            val seen = HashSet<String>()
            val out = ArrayList<Pair<String, String>>()
            for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
                val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
                for (ri in pm.queryIntentActivities(q, 0)) {
                    val p = ri.activityInfo.packageName
                    if (p == packageName || !seen.add(p)) continue
                    out.add(Pair(p, ri.loadLabel(pm).toString()))
                }
            }
            out.sortBy { it.second.lowercase() }
            val arr = JSONArray()
            for (o in out) arr.put(JSONObject().put("pkg", o.first).put("label", o.second))
            return arr.toString()
        }

        @JavascriptInterface
        fun getIcon(pkg: String, banner: Boolean): String {
            return try {
                val pm = packageManager
                val b: Drawable? = if (banner) pm.getApplicationBanner(pkg) else null
                val dr: Drawable = b ?: pm.getApplicationIcon(pkg)
                val isB = b != null
                val w = if (isB) 320 else 160
                val h = if (isB) 180 else 160
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                dr.setBounds(0, 0, w, h)
                dr.draw(Canvas(bmp))
                val bs = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 100, bs)
                (if (isB) "b|" else "i|") + "data:image/png;base64," +
                        Base64.encodeToString(bs.toByteArray(), Base64.NO_WRAP)
            } catch (e: Exception) {
                ""
            }
        }

        @JavascriptInterface
        fun launch(pkg: String) {
            runOnUiThread {
                try {
                    val pm = packageManager
                    val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
                    if (i != null) {
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(i)
                    }
                } catch (e: Exception) {
                }
            }
        }

        @JavascriptInterface
        fun getInputs(): String {
            val arr = JSONArray()
            try {
                val tm = getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
                for (ti in tm.tvInputList) {
                    if (ti.isPassthroughInput) {
                        val label = (ti.loadCustomLabel(this@MainActivity) ?: ti.loadLabel(this@MainActivity)).toString()
                        arr.put(JSONObject().put("id", ti.id).put("label", label))
                    }
                }
            } catch (e: Exception) {
            }
            return arr.toString()
        }

        @JavascriptInterface
        fun startInput(id: String) {
            runOnUiThread {
                try {
                    val u = TvContract.buildChannelUriForPassthroughInput(id)
                    startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                }
            }
        }

        @JavascriptInterface
        fun openSettings(kind: String) {
            runOnUiThread {
                try {
                    val a = if (kind == "wifi") Settings.ACTION_WIFI_SETTINGS else Settings.ACTION_SETTINGS
                    startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                }
            }
        }

        @JavascriptInterface
        fun getNet(): String {
            return try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val c = cm.getNetworkCapabilities(cm.activeNetwork)
                when {
                    c == null -> "none"
                    c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                    c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                    else -> "other"
                }
            } catch (e: Exception) {
                "none"
            }
        }
    }
}
