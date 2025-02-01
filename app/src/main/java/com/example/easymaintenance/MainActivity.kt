package com.example.easymaintenance

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var capturedImageUri: Uri? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private var webView: WebView? = null
    private var doubleBackToExitPressedOnce = false

    private val CAMERA_PERMISSION = Manifest.permission.CAMERA
    private val REQUEST_CODE_CAMERA = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request Camera Permission
        requestCameraPermission()

        // Initialize file chooser
        fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val dataUri = result.data?.data
                val resultUri = dataUri ?: capturedImageUri
                fileUploadCallback?.onReceiveValue(resultUri?.let { arrayOf(it) })
            } else {
                fileUploadCallback?.onReceiveValue(null)
            }
            fileUploadCallback = null
            capturedImageUri = null
        }

        setContent {
            WebViewScreen()
        }
    }

    @Composable
    fun WebViewScreen() {
        webView = remember { WebView(this) }

        AndroidView(factory = { context ->
            webView!!.apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    loadsImagesAutomatically = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    setUseWideViewPort(true)
                    setLoadWithOverviewMode(true)
                    userAgentString = "Mozilla/5.0 (Linux; Android 10; Pixel 4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url.toString()
                        return if (url.startsWith("http")) {
                            false
                        } else {
                            try {
                                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "Unable to open link", Toast.LENGTH_SHORT).show()
                                Log.e("WebView", "Error opening link: $url", e)
                            }
                            true
                        }
                    }

                    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                        super.onReceivedError(view, errorCode, description, failingUrl)
                        Log.e("WebViewError", "Error: $errorCode, $description, URL: $failingUrl")
                        Toast.makeText(context, "Failed to load page", Toast.LENGTH_SHORT).show()
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(
                        webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?
                    ): Boolean {
                        if (fileUploadCallback != null) {
                            fileUploadCallback?.onReceiveValue(null)
                        }
                        fileUploadCallback = filePathCallback

                        val acceptTypes = fileChooserParams?.acceptTypes?.joinToString()
                        val isCameraCapture = acceptTypes?.contains("image") == true && fileChooserParams?.isCaptureEnabled == true

                        try {
                            val intent = if (isCameraCapture) {
                                if (ContextCompat.checkSelfPermission(this@MainActivity, CAMERA_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
                                    capturedImageUri = createImageUri()
                                    Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                                        putExtra(MediaStore.EXTRA_OUTPUT, capturedImageUri)
                                    }
                                } else {
                                    requestCameraPermission()
                                    return false
                                }
                            } else {
                                fileChooserParams?.createIntent()
                            }
                            fileChooserLauncher.launch(intent)
                        } catch (e: Exception) {
                            fileUploadCallback = null
                            Toast.makeText(context, "Error opening file chooser", Toast.LENGTH_SHORT).show()
                            return false
                        }
                        return true
                    }
                }

                loadUrl("https://app.easymaintenance.in")
            }
        }, modifier = Modifier.fillMaxSize())
    }

    private fun createImageUri(): Uri {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)!!
    }

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(CAMERA_PERMISSION), REQUEST_CODE_CAMERA)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Camera permission denied. Enable it manually in settings.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            if (doubleBackToExitPressedOnce) {
                super.onBackPressed()
                return
            }

            this.doubleBackToExitPressedOnce = true
            Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show()

            Handler(Looper.getMainLooper()).postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
        }
    }
}
