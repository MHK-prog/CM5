package com.mhkprog.cm5

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.webkit.WebViewAssetLoader
import java.util.Base64

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val filePickerRequestCode = 5105

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        webView.addJavascriptInterface(CM5Bridge(), "CM5")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.host != "appassets.androidplatform.net"
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = callback
                val acceptsJson = params.acceptTypes.any { type ->
                    type.contains("json", ignoreCase = true) || type.contains(".json", ignoreCase = true)
                }
                val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = if (acceptsJson) "application/json" else "image/*"
                    if (acceptsJson) putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/json", "text/plain"))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                return try {
                    startActivityForResult(Intent.createChooser(picker, if (acceptsJson) "انتخاب فایل پشتیبان" else "انتخاب عکس"), filePickerRequestCode)
                    true
                } catch (_: Exception) {
                    fileChooserCallback = null
                    false
                }
            }
        }

        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != filePickerRequestCode) return
        val result = if (resultCode == RESULT_OK) data?.data?.let { arrayOf(it) } else null
        fileChooserCallback?.onReceiveValue(result)
        fileChooserCallback = null
    }

    private inner class CM5Bridge {
        @JavascriptInterface
        fun saveFile(fileName: String, mimeType: String, encodedData: String): String {
            return try {
                val safeName = fileName.replace(Regex("[^\\p{L}\\p{N}._-]"), "_").take(80).ifBlank { "CM5-file" }
                val bytes = Base64.getDecoder().decode(encodedData)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/CM5")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return "ذخیره در پوشه‌ی بارگیری انجام نشد."
                resolver.openOutputStream(uri)?.use { output -> output.write(bytes) }
                    ?: return "فایل خروجی باز نشد."
                val completed = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, completed, null, null)
                "saved"
            } catch (error: Exception) {
                error.message ?: "ذخیره‌ی فایل انجام نشد."
            }
        }

        @JavascriptInterface
        fun printReport() {
            mainHandler.post {
                try {
                    val printManager = getSystemService(PRINT_SERVICE) as PrintManager
                    val adapter = webView.createPrintDocumentAdapter("CM5-Report")
                    val attributes = PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize("ISO_A5", "A5", 5830, 8270))
                        .setResolution(PrintAttributes.Resolution("cm5-300", "CM5 300 DPI", 300, 300))
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()
                    printManager.print("CM 5.0 - کارنامه", adapter, attributes)
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "بازکردن چاپگر انجام نشد.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("CM5")
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
