package com.mhkprog.cm5

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Activity
import android.content.ContentValues
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.widget.FrameLayout
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentInfo
import android.print.PrintDocumentAdapter
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.Locale
import java.util.UUID

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var rootView: FrameLayout
    private val mainHandler = Handler(Looper.getMainLooper())
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraUri: Uri? = null
    private var reportPrintWebView: WebView? = null
    private var backupDirectoryUri: Uri? = null
    private var backupImagesDirectoryUri: Uri? = null
    private val reportPdfLock = Any()
    @Volatile private var reportPdfExportInProgress = false
    @Volatile private var updateCheckInProgress = false
    @Volatile private var backupFolderPickerActive = false
    @Volatile private var restoreFolderPickerActive = false
    private val restoredBackupLock = Any()
    private var restoredBackupJson: String? = null
    private val temporaryCameraPhotoUris = mutableMapOf<String, Uri>()
    private var unclaimedCameraPhotoToken: String? = null
    private val cameraPhotoLock = Any()
    private val filePickerRequestCode = 5105
    private val backupFolderRequestCode = 5205
    private val restoreFolderRequestCode = 5206
    private val updateApiUrl = "https://api.github.com/repos/MHK-prog/CM5/releases/latest"
    private val releaseDownloadUrl = "https://github.com/MHK-prog/CM5/releases/latest/download/MoeallemYarDamavand.apk"

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
        checkForAppUpdate(manual = false)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == restoreFolderRequestCode) {
            restoreFolderPickerActive = false
            if (resultCode != RESULT_OK || data?.data == null) {
                notifyBackupFolderLoaded(false)
                return
            }
            val treeUri = data.data!!
            val grantFlags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            runCatching {
                if (grantFlags != 0) contentResolver.takePersistableUriPermission(treeUri, grantFlags)
            }
            Thread {
                val result = runCatching { loadBackupFolder(treeUri) }
                result.onSuccess { json ->
                    synchronized(restoredBackupLock) { restoredBackupJson = json }
                    mainHandler.post { notifyBackupFolderLoaded(true) }
                }.onFailure {
                    synchronized(restoredBackupLock) { restoredBackupJson = null }
                    mainHandler.post { notifyBackupFolderLoaded(false) }
                }
            }.start()
            return
        }
        if (requestCode == backupFolderRequestCode) {
            backupFolderPickerActive = false
            if (resultCode != RESULT_OK || data?.data == null) {
                notifyBackupFolderReady(false)
                return
            }
            try {
                val treeUri = data.data!!
                val grantFlags = data.flags and
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                if (grantFlags != 0) contentResolver.takePersistableUriPermission(treeUri, grantFlags)
                val treeDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri)
                )
                val timestamp = System.currentTimeMillis()
                backupDirectoryUri = DocumentsContract.createDocument(
                    contentResolver,
                    treeDocumentUri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    "CM5_Backup_$timestamp"
                ) ?: throw IllegalStateException("Could not create backup directory")
                backupImagesDirectoryUri = DocumentsContract.createDocument(
                    contentResolver,
                    backupDirectoryUri!!,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    "images"
                ) ?: throw IllegalStateException("Could not create image directory")
                notifyBackupFolderReady(true)
            } catch (_: Exception) {
                backupDirectoryUri = null
                backupImagesDirectoryUri = null
                notifyBackupFolderReady(false)
            }
            return
        }
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

    private fun notifyBackupFolderReady(success: Boolean) {
        if (::webView.isInitialized) {
            webView.evaluateJavascript("window.cm5BackupFolderReady?.($success)", null)
        }
    }

    private fun notifyBackupFolderLoaded(success: Boolean) {
        if (::webView.isInitialized && !isDestroyed) {
            webView.evaluateJavascript("window.cm5BackupFolderLoaded?.($success)", null)
        }
    }

    private data class BackupTreeEntry(val id: String, val name: String, val mimeType: String)

    private fun queryBackupTreeEntries(treeUri: Uri, parentDocumentId: String): List<BackupTreeEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val entries = mutableListOf<BackupTreeEntry>()
        contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val typeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                entries += BackupTreeEntry(
                    cursor.getString(idColumn),
                    cursor.getString(nameColumn) ?: "",
                    cursor.getString(typeColumn) ?: ""
                )
            }
        }
        return entries
    }

    private fun loadBackupFolder(treeUri: Uri): String {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootEntries = queryBackupTreeEntries(treeUri, rootId)
        val backupFolder = if (rootEntries.any { it.name == "CM5_backup.json" }) {
            null
        } else {
            rootEntries
                .filter {
                    it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR &&
                        it.name.startsWith("CM5_Backup_")
                }
                .sortedByDescending { it.name }
                .firstOrNull { folder ->
                    queryBackupTreeEntries(treeUri, folder.id).any { it.name == "CM5_backup.json" }
                }
        }
        val folderId = backupFolder?.id ?: rootId
        val files = queryBackupTreeEntries(treeUri, folderId)
        val jsonEntry = files.firstOrNull { it.name == "CM5_backup.json" }
            ?: throw IllegalArgumentException("فایل JSON پشتیبان پیدا نشد.")
        val imagesEntry = files.firstOrNull {
            it.name == "images" && it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR
        }
        val jsonUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, jsonEntry.id)
        val jsonText = contentResolver.openInputStream(jsonUri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: throw IllegalStateException("خواندن فایل JSON پشتیبان انجام نشد.")
        val payload = JSONObject(jsonText)
        val classes = payload.optJSONArray("classes") ?: throw IllegalArgumentException("اطلاعات کلاس‌ها پیدا نشد.")
        val imageEntries = imagesEntry?.let { queryBackupTreeEntries(treeUri, it.id) }.orEmpty()
        val imageByName = imageEntries.associateBy { it.name }
        for (classIndex in 0 until classes.length()) {
            val students = classes.optJSONObject(classIndex)?.optJSONArray("students") ?: continue
            for (studentIndex in 0 until students.length()) {
                val student = students.optJSONObject(studentIndex) ?: continue
                val photoPath = student.optString("photoFile")
                val match = Regex("^images/([A-Za-z0-9._-]{1,100})$").matchEntire(photoPath) ?: continue
                val imageEntry = imageByName[match.groupValues[1]] ?: continue
                val mimeType = when (imageEntry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    else -> continue
                }
                val imageUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, imageEntry.id)
                val bytes = contentResolver.openInputStream(imageUri)?.use { it.readBytes() } ?: continue
                student.put("photo", "data:$mimeType;base64,${Base64.getEncoder().encodeToString(bytes)}")
                student.remove("photoFile")
            }
        }
        return payload.toString()
    }

    private fun startReportPdfExport(fileName: String, html: String) {
        if (!::rootView.isInitialized || isFinishing || isDestroyed) {
            finishReportPdfExport(false, "ساخت PDF انجام نشد.")
            return
        }
        val printView = WebView(this).apply {
            visibility = View.INVISIBLE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
        }
        rootView.addView(
            printView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        reportPrintWebView = printView
        var printStarted = false
        printView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (printStarted) return
                printStarted = true
                val waitForAssets = """(async()=>{
                    const images=Array.from(document.images);
                    await Promise.race([
                      Promise.all(images.map(image=>image.complete?Promise.resolve():new Promise(resolve=>{image.onload=resolve;image.onerror=resolve}))),
                      new Promise(resolve=>setTimeout(resolve,15000))
                    ]);
                    if(document.fonts?.ready) await Promise.race([document.fonts.ready,new Promise(resolve=>setTimeout(resolve,8000))]);
                    return true;
                  })()""".trimIndent()
                view.evaluateJavascript(waitForAssets) {
                    mainHandler.post { writePrintWebViewToPdf(view, fileName) }
                }
            }
        }
        printView.loadDataWithBaseURL(
            "https://appassets.androidplatform.net/assets/",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun writePrintWebViewToPdf(printView: WebView, fileName: String) {
        val adapter = try {
            printView.createPrintDocumentAdapter(fileName.removeSuffix(".pdf"))
        } catch (error: Exception) {
            finishReportPdfExport(false, error.message ?: "ساخت PDF انجام نشد.")
            return
        }
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A5)
            .setResolution(PrintAttributes.Resolution("cm5_pdf", "CM5 PDF", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        adapter.onLayout(
            null,
            attributes,
            CancellationSignal(),
            object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) {
                    if (info.pageCount < 1) {
                        adapter.onFinish()
                        finishReportPdfExport(false, "هیچ صفحه‌ای برای ذخیره پیدا نشد.")
                        return
                    }
                    val tempFile = File(cacheDir, "cm5_report_${System.currentTimeMillis()}.pdf")
                    val descriptor = try {
                        ParcelFileDescriptor.open(
                            tempFile,
                            ParcelFileDescriptor.MODE_CREATE or
                                ParcelFileDescriptor.MODE_TRUNCATE or
                                ParcelFileDescriptor.MODE_READ_WRITE
                        )
                    } catch (error: Exception) {
                        adapter.onFinish()
                        finishReportPdfExport(false, error.message ?: "فایل موقت PDF ساخته نشد.")
                        return
                    }
                    adapter.onWrite(
                        arrayOf(PageRange(0, info.pageCount - 1)),
                        descriptor,
                        CancellationSignal(),
                        object : PrintDocumentAdapter.WriteResultCallback() {
                            override fun onWriteFinished(pages: Array<out PageRange>) {
                                runCatching { descriptor.close() }
                                val result = runCatching { copyReportPdfToDownloads(tempFile, fileName) }
                                tempFile.delete()
                                adapter.onFinish()
                                finishReportPdfExport(
                                    result.isSuccess,
                                    result.exceptionOrNull()?.message ?: "ذخیره PDF انجام نشد."
                                )
                            }

                            override fun onWriteFailed(error: CharSequence?) {
                                runCatching { descriptor.close() }
                                tempFile.delete()
                                adapter.onFinish()
                                finishReportPdfExport(false, error?.toString() ?: "نوشتن PDF انجام نشد.")
                            }

                            override fun onWriteCancelled() {
                                runCatching { descriptor.close() }
                                tempFile.delete()
                                adapter.onFinish()
                                finishReportPdfExport(false, "ساخت PDF لغو شد.")
                            }
                        }
                    )
                }

                override fun onLayoutFailed(error: CharSequence?) {
                    adapter.onFinish()
                    finishReportPdfExport(false, error?.toString() ?: "صفحه‌بندی PDF انجام نشد.")
                }

                override fun onLayoutCancelled() {
                    adapter.onFinish()
                    finishReportPdfExport(false, "ساخت PDF لغو شد.")
                }
            },
            Bundle()
        )
    }

    private fun copyReportPdfToDownloads(source: File, fileName: String) {
        val safeName = fileName
            .replace(Regex("[^\\p{L}\\p{N}._-]"), "_")
            .take(100)
            .ifBlank { "CM5_reports.pdf" }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/CM5")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("ساخت فایل در پوشه بارگیری انجام نشد.")
        try {
            FileInputStream(source).use { input ->
                contentResolver.openOutputStream(uri)?.use { output -> input.copyTo(output) }
                    ?: throw IllegalStateException("فایل PDF برای نوشتن باز نشد.")
            }
            val completed = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            contentResolver.update(uri, completed, null, null)
        } catch (error: Exception) {
            runCatching { contentResolver.delete(uri, null, null) }
            throw error
        }
    }

    private fun finishReportPdfExport(success: Boolean, message: String) {
        mainHandler.post {
            val printView = reportPrintWebView
            if (printView != null) {
                printView.stopLoading()
                (printView.parent as? ViewGroup)?.removeView(printView)
                printView.destroy()
                reportPrintWebView = null
            }
            synchronized(reportPdfLock) { reportPdfExportInProgress = false }
            if (::webView.isInitialized && !isDestroyed) {
                val quotedMessage = JSONObject.quote(message)
                webView.evaluateJavascript(
                    "window.cm5ReportPdfFinished?.($success,$quotedMessage)",
                    null
                )
            }
        }
    }

    private fun notifyUpdateStatus(status: String) {
        if (::webView.isInitialized && !isDestroyed) {
            webView.evaluateJavascript(
                "window.cm5UpdateCheckFinished?.(${JSONObject.quote(status)})",
                null
            )
        }
    }

    private fun checkForAppUpdate(manual: Boolean) {
        synchronized(this) {
            if (updateCheckInProgress) return
            updateCheckInProgress = true
        }
        Thread {
            var connection: HttpURLConnection? = null
            var resultStatus = "error"
            try {
                connection = (URL(updateApiUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 7000
                    readTimeout = 7000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    setRequestProperty("User-Agent", "MoeallemYarDamavand-Android")
                }
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val latestVersion = JSONObject(response).optString("tag_name").removePrefix("v")
                        if (latestVersion.isNotBlank() && compareVersions(latestVersion, BuildConfig.VERSION_NAME) > 0) {
                            resultStatus = "available"
                            mainHandler.post { showUpdateDialog(latestVersion) }
                        } else {
                            resultStatus = "current"
                        }
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> resultStatus = "not_published"
                    else -> resultStatus = "error"
                }
            } catch (_: Exception) {
                resultStatus = "error"
            } finally {
                connection?.disconnect()
                synchronized(this) { updateCheckInProgress = false }
                if (manual && resultStatus != "available") {
                    mainHandler.post { notifyUpdateStatus(resultStatus) }
                }
            }
        }.start()
    }

    private fun compareVersions(first: String, second: String): Int {
        val left = Regex("\\d+").findAll(first).mapNotNull { it.value.toLongOrNull() }.toList()
        val right = Regex("\\d+").findAll(second).mapNotNull { it.value.toLongOrNull() }.toList()
        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0L }
            val b = right.getOrElse(index) { 0L }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    private fun showUpdateDialog(latestVersion: String) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle("نسخه جدید معلم یار دماوند")
            .setMessage("نسخه $latestVersion منتشر شده است. برای دریافت و نصب، لینک دانلود را باز کنید.")
            .setPositiveButton("دریافت نسخه جدید") { _, _ ->
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releaseDownloadUrl)))
                }.onFailure { notifyUpdateStatus("error") }
            }
            .setNegativeButton("بعداً", null)
            .show()
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
        fun saveReportPdf(fileName: String, html: String): String {
            if (html.isBlank()) return "محتوای کارنامه خالی است."
            val canStart = synchronized(reportPdfLock) {
                if (reportPdfExportInProgress) false
                else {
                    reportPdfExportInProgress = true
                    true
                }
            }
            if (!canStart) return "ساخت PDF دیگری در حال انجام است."
            mainHandler.post {
                runCatching { startReportPdfExport(fileName, html) }
                    .onFailure { error -> finishReportPdfExport(false, error.message ?: "ساخت PDF انجام نشد.") }
            }
            return "started"
        }

        @JavascriptInterface
        fun chooseBackupFolder(): String {
            if (backupFolderPickerActive) return "انتخاب پوشه در حال انجام است."
            backupFolderPickerActive = true
            mainHandler.post {
                try {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                        )
                    }
                    startActivityForResult(intent, backupFolderRequestCode)
                } catch (_: Exception) {
                    backupFolderPickerActive = false
                    notifyBackupFolderReady(false)
                }
            }
            return "started"
        }

        @JavascriptInterface
        fun chooseRestoreFolder(): String {
            if (restoreFolderPickerActive) return "انتخاب پوشه در حال انجام است."
            synchronized(restoredBackupLock) { restoredBackupJson = null }
            restoreFolderPickerActive = true
            mainHandler.post {
                try {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                        )
                    }
                    startActivityForResult(intent, restoreFolderRequestCode)
                } catch (_: Exception) {
                    restoreFolderPickerActive = false
                    notifyBackupFolderLoaded(false)
                }
            }
            return "started"
        }

        @JavascriptInterface
        fun takeRestoredBackupJson(): String {
            return synchronized(restoredBackupLock) {
                restoredBackupJson.also { restoredBackupJson = null } ?: ""
            }
        }

        @JavascriptInterface
        fun writeBackupFile(relativePath: String, mimeType: String, encodedData: String): String {
            val isJson = relativePath == "CM5_backup.json"
            val isImage = relativePath.startsWith("images/") &&
                relativePath.substringAfter("images/").matches(Regex("[A-Za-z0-9._-]{1,100}"))
            if (!isJson && !isImage) return "مسیر فایل پشتیبان نامعتبر است."
            if (isJson && mimeType != "application/json") return "نوع فایل JSON نامعتبر است."
            if (isImage && mimeType !in setOf("image/jpeg", "image/png")) return "نوع تصویر پشتیبان پشتیبانی نمی‌شود."
            val parentUri = (if (isImage) backupImagesDirectoryUri else backupDirectoryUri)
                ?: return "ابتدا یک پوشه برای پشتیبان انتخاب کنید."
            var createdUri: Uri? = null
            return try {
                val fileName = if (isJson) relativePath else relativePath.substringAfterLast('/')
                val fileUri = DocumentsContract.createDocument(contentResolver, parentUri, mimeType, fileName)
                    ?: throw IllegalStateException("ساخت فایل پشتیبان انجام نشد.")
                createdUri = fileUri
                val bytes = Base64.getDecoder().decode(encodedData)
                contentResolver.openOutputStream(fileUri)?.use { output -> output.write(bytes) }
                    ?: throw IllegalStateException("فایل پشتیبان برای نوشتن باز نشد.")
                if (isJson) {
                    backupDirectoryUri = null
                    backupImagesDirectoryUri = null
                }
                "saved"
            } catch (error: Exception) {
                createdUri?.let { uri -> runCatching { contentResolver.delete(uri, null, null) } }
                error.message ?: "ذخیره فایل پشتیبان انجام نشد."
            }
        }

        @JavascriptInterface
        fun checkForUpdates(): String {
            checkForAppUpdate(manual = true)
            return "started"
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
        reportPrintWebView?.let { printView ->
            (printView.parent as? ViewGroup)?.removeView(printView)
            printView.destroy()
            reportPrintWebView = null
        }
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("CM5")
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
