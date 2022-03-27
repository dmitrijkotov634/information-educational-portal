package com.dm.iop

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.dm.iop.databinding.ActivityMainBinding
import com.dm.iop.databinding.SettingsDialogBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Connection
import org.jsoup.Jsoup


class MainActivity : AppCompatActivity() {
    private val mainBinding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(
            layoutInflater
        )
    }

    private val preferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(
            this
        )
    }

    private val cookieManager: CookieManager by lazy { CookieManager.getInstance() }

    private val scope = MainScope()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(mainBinding.root)

        mainBinding.webview.settings.javaScriptEnabled = true
        mainBinding.webview.settings.builtInZoomControls = true
        mainBinding.webview.settings.domStorageEnabled = true
        mainBinding.webview.settings.displayZoomControls = false

        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_YES -> {
                    WebSettingsCompat.setForceDark(
                        mainBinding.webview.settings,
                        WebSettingsCompat.FORCE_DARK_ON
                    )
                }

                Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_UNDEFINED -> {
                    WebSettingsCompat.setForceDark(
                        mainBinding.webview.settings,
                        WebSettingsCompat.FORCE_DARK_OFF
                    )
                }
            }
        }

        mainBinding.webview.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                view?.loadUrl(url!!)

                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                title = view!!.title

                super.onPageFinished(view, url)
            }
        }

        mainBinding.webview.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                mainBinding.progressBar.visibility =
                    if (newProgress == 100) View.GONE else View.VISIBLE
                mainBinding.progressBar.progress = newProgress

                super.onProgressChanged(view, newProgress)
            }
        }

        mainBinding.webview.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            val request = DownloadManager.Request(Uri.parse(url))

            request.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
            request.addRequestHeader("User-Agent", userAgent)

            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

            val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager

            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                URLUtil.guessFileName(url, contentDisposition, mimetype)
            )

            downloadManager.enqueue(request)
        }

        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(mainBinding.webview, true)

        checkAndLogin()
    }

    override fun onBackPressed() {
        if (mainBinding.webview.canGoBack())
            mainBinding.webview.goBack()
        else
            super.onBackPressed()
    }

    private fun checkAndLogin() {
        if (preferences.getString(KEY_LOGIN, "").isNullOrEmpty() ||
            preferences.getString(KEY_PASSWORD, "").isNullOrEmpty()
        ) {
            val settingsBinding = SettingsDialogBinding.inflate(layoutInflater)

            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.authorization)
                .setView(settingsBinding.root)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    with(preferences.edit()) {
                        putString(KEY_LOGIN, settingsBinding.login.editText?.text.toString())
                        putString(KEY_PASSWORD, settingsBinding.password.editText?.text.toString())
                        apply()
                    }

                    checkAndLogin()
                }
                .setCancelable(false)
                .show()

            return
        }

        title = getString(R.string.authorization) + "..."


        scope.launch {
            val cookie = StringBuilder()

            for ((k, v) in loginMoodle(
                preferences.getString(KEY_LOGIN, "")!!,
                preferences.getString(KEY_PASSWORD, "")!!
            ))
                cookie.append("$k=$v; ")

            cookieManager.setCookie(DEFAULT_URL, cookie.toString())

            runOnUiThread {
                mainBinding.webview.loadUrl(DEFAULT_URL)

                title = getString(R.string.app_name)
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun loginMoodle(username: String, password: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            val res = Jsoup
                .connect(DEFAULT_LOGIN_URL)
                .data("username", username, "password", password, "rememberusername", "1")
                .method(Connection.Method.POST)
                .execute()

            return@withContext res.cookies()
        }

    companion object {
        const val KEY_LOGIN = "login"
        const val KEY_PASSWORD = "password"

        const val DEFAULT_LOGIN_URL = "https://www.mivlgu.ru/iop/login/index.php"
        const val DEFAULT_URL = "https://www.mivlgu.ru/iop/"
    }
}