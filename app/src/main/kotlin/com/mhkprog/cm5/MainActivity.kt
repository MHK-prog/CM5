package com.mhkprog.cm5

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.MediaStore
import android.widget.FrameLayout
import android.view.ViewGroup
import android.view.WindowInsets
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import java.util.Base64
import java.util.UUID

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var rootView: FrameLayout
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraUri: Uri? = null
    private val temporaryCameraPhotoUris = mutableMapOf<String, Uri>()
    private var unclaimedCameraPhotoToken: String? = null
    private val cameraPhotoLock = Any()
    private val filePickerRequestCode = 5105

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(247, 251, 255)
        window.navigationBarColor = Color.WHITE
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }

        webView = WebView(this)
        webView.setBackgroundColor(Color.rgb(247, 251, 255))
        rootView = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(247, 251, 255))
            addView(
                webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        @Suppress("DEPRECATION")
        rootView.setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val handledTypes = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                val safeInsets = insets.getInsets(handledTypes)
                val layoutParams = webView.layoutParams as FrameLayout.LayoutParams
                layoutParams.setMargins(
                    safeInsets.left,
                    safeInsets.top,
                    safeInsets.right,
                    safeInsets.bottom
                )
                webView.layoutParams = layoutParams
                WindowInsets.Builder(insets)
                    .setInsets(handledTypes, android.graphics.Insets.NONE)
                    .setInsetsIgnoringVisibility(WindowInsets.Type.systemBars(), android.graphics.Insets.NONE)
                    .build()
            } else {
                insets
            }
        }
        setContentView(rootView)
        rootView.post { rootView.requestApplyInsets() }
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
                discardPendingCameraCapture()
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
                val chooser = Intent.createChooser(
                    picker,
                    if (acceptsJson) "انتخاب فایل پشتیبان" else "انتخاب یا گرفتن عکس"
                )
                if (!acceptsJson) {
                    val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    if (cameraIntent.resolveActivity(packageManager) != null) {
                        createCameraCaptureUri()?.let { captureUri ->
                            pendingCameraUri = captureUri
                            cameraIntent.apply {
                                putExtra(MediaStore.EXTRA_OUTPUT, captureUri)
                                clipData = ClipData.newRawUri("CM5 profile photo", captureUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            }
                            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
                        }
                    }
                }
                return try {
                    startActivityForResult(chooser, filePickerRequestCode)
                    true
                } catch (_: Exception) {
                    discardPendingCameraCapture()
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
        val captureUri = pendingCameraUri
        val selectedUri = if (resultCode == RESULT_OK) data?.data ?: captureUri else null
        if (captureUri != null) {
            if (selectedUri == captureUri) {
                val completed = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                contentResolver.update(captureUri, completed, null, null)
                val token = UUID.randomUUID().toString()
                synchronized(cameraPhotoLock) {
                    temporaryCameraPhotoUris[token] = captureUri
                    unclaimedCameraPhotoToken = token
                }
            } else {
                contentResolver.delete(captureUri, null, null)
            }
        }
        pendingCameraUri = null
        val result = selectedUri?.let { arrayOf(it) }
        fileChooserCallback?.onReceiveValue(result)
        fileChooserCallback = null
    }

    private fun createCameraCaptureUri(): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "CM5_Profile_${System.currentTimeMillis()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/CM5")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun discardPendingCameraCapture() {
        pendingCameraUri?.let { uri -> runCatching { contentResolver.delete(uri, null, null) } }
        pendingCameraUri = null
    }

    private fun discardTemporaryCameraPhoto(token: String) {
        val uri = synchronized(cameraPhotoLock) { temporaryCameraPhotoUris.remove(token) }
        uri?.let { cameraUri -> runCatching { contentResolver.delete(cameraUri, null, null) } }
    }

    private fun discardAllTemporaryCameraPhotos() {
        val uris = synchronized(cameraPhotoLock) {
            unclaimedCameraPhotoToken = null
            temporaryCameraPhotoUris.values.toList().also { temporaryCameraPhotoUris.clear() }
        }
        uris.forEach { uri -> runCatching { contentResolver.delete(uri, null, null) } }
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
        fun saveReportPdf(fileName: String, encodedPng: String): String {
            var outputUri: Uri? = null
            var pdf: PdfDocument? = null
            var bitmap: Bitmap? = null
            return try {
                val pngBytes = Base64.getDecoder().decode(encodedPng)
                val reportBitmap = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
                    ?: throw IllegalArgumentException("تصویر کارنامه خوانده نشد.")
                bitmap = reportBitmap

                val document = PdfDocument()
                pdf = document
                val pageInfo = PdfDocument.PageInfo.Builder(420, 595, 1).create()
                val page = document.startPage(pageInfo)
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(
                    reportBitmap,
                    null,
                    Rect(0, 0, 420, 595),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                )
                document.finishPage(page)

                val safeName = fileName
                    .replace(Regex("[^\\p{L}\\p{N}._-]"), "_")
                    .take(80)
                    .ifBlank { "CM5-report.pdf" }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/CM5")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("ذخیره در پوشهٔ بارگیری انجام نشد.")
                outputUri = uri
                resolver.openOutputStream(uri)?.use { output -> document.writeTo(output) }
                    ?: throw IllegalStateException("فایل PDF باز نشد.")
                val completed = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, completed, null, null)
                "saved"
            } catch (error: Exception) {
                outputUri?.let { uri -> runCatching { contentResolver.delete(uri, null, null) } }
                error.message ?: "ساخت یا ذخیرهٔ PDF انجام نشد."
            } finally {
                pdf?.close()
                bitmap?.recycle()
            }
        }

        @JavascriptInterface
        fun takeCameraPhotoToken(): String {
            return synchronized(cameraPhotoLock) {
                unclaimedCameraPhotoToken?.also { unclaimedCameraPhotoToken = null } ?: ""
            }
        }

        @JavascriptInterface
        fun finishCameraPhoto(token: String) {
            discardTemporaryCameraPhoto(token)
        }
    }

    override fun onDestroy() {
        discardPendingCameraCapture()
        discardAllTemporaryCameraPhotos()
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("CM5")
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
