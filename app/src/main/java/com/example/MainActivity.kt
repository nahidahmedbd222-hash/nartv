package com.example

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var mWebView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen()
            }
        }
    }

    override fun onDestroy() {
        mWebView?.let { webView ->
            webView.stopLoading()
            webView.clearHistory()
            webView.destroy()
        }
        mWebView = null
        super.onDestroy()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // WebView States
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("https://tv.rootitsystem.com/") }
    var isLoading by remember { mutableStateOf(true) }
    var loadingProgress by remember { mutableStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    // Ad Blocker States
    var adBlockEnabled by remember { mutableStateOf(true) }
    var blockedCount by remember { mutableStateOf(0) }

    // UI controls visibility
    var showControls by remember { mutableStateOf(true) }

    // Custom View (Full screen video support)
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // Intercept hardware Back Button
    BackHandler(enabled = true) {
        if (customView != null) {
            // Close fullscreen video first
            customViewCallback?.onCustomViewHidden()
            customView = null
            customViewCallback = null
        } else if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            // Traditional finish if cannot navigate backwards anymore
            (context as? ComponentActivity)?.finish()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0E))
    ) {
        if (customView != null) {
            // HTML5 Fullscreen Video Overlay
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        val parentViewGroup = customView?.parent as? ViewGroup
                        parentViewGroup?.removeView(customView)
                        addView(
                            customView,
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Normal WebView and Dashboard layout
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Animated Top dashboard menu bar
                AnimatedVisibility(
                    visible = showControls,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("controls_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF16161A).copy(alpha = 0.94f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            // First Row: Title, active Blocker Indicator, Close button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Root IT TV",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = currentUrl.replace("https://", "").replace("http://", ""),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Color.Gray
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Blocker Status Badge
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (adBlockEnabled) Color(0xFF00C853).copy(alpha = 0.15f)
                                            else Color(0xFFFF5252).copy(alpha = 0.15f)
                                        )
                                        .clickable {
                                            adBlockEnabled = !adBlockEnabled
                                            webViewRef?.reload()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (adBlockEnabled) Icons.Default.Check else Icons.Default.Warning,
                                        contentDescription = "Shield Blocker Badge",
                                        tint = if (adBlockEnabled) Color(0xFF00C853) else Color(0xFFFF5252),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (adBlockEnabled) "$blockedCount Blocked" else "Ads Allowed",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (adBlockEnabled) Color(0xFF00C853) else Color(0xFFFF5252)
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // Close panel button
                                IconButton(
                                    onClick = { showControls = false },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close overlay controls",
                                        tint = Color.LightGray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Second Row: Interactive back/forward/refresh buttons + block slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Navigator Back
                                    IconButton(
                                        onClick = { webViewRef?.goBack() },
                                        enabled = canGoBack,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                if (canGoBack) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Go Back",
                                            tint = if (canGoBack) Color.White else Color.DarkGray
                                        )
                                    }

                                    // Navigator Forward
                                    IconButton(
                                        onClick = { webViewRef?.goForward() },
                                        enabled = canGoForward,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                if (canGoForward) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = "Go Forward",
                                            tint = if (canGoForward) Color.White else Color.DarkGray
                                        )
                                    }

                                    // Reset to main tv page (Home)
                                    IconButton(
                                        onClick = { webViewRef?.loadUrl("https://tv.rootitsystem.com/") },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Home,
                                            contentDescription = "Reset Home",
                                            tint = Color.White
                                        )
                                    }

                                    // Refresh screen
                                    IconButton(
                                        onClick = { webViewRef?.reload() },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Reload stream",
                                            tint = Color.White
                                        )
                                    }
                                }

                                // Interactive Blocker Slider Pill
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            adBlockEnabled = !adBlockEnabled
                                            webViewRef?.reload()
                                        }
                                        .padding(horizontal = 6.dp)
                                ) {
                                    Text(
                                        text = "AdBlock",
                                        fontSize = 11.sp,
                                        color = Color.LightGray,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                    Switch(
                                        checked = adBlockEnabled,
                                        onCheckedChange = {
                                            adBlockEnabled = it
                                            webViewRef?.reload()
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFF00C853),
                                            checkedTrackColor = Color(0xFF00C853).copy(alpha = 0.4f),
                                            uncheckedThumbColor = Color.LightGray,
                                            uncheckedTrackColor = Color.DarkGray
                                        ),
                                        modifier = Modifier.scale(0.85f)
                                    )
                                }
                            }

                            // Dynamic Linear Progress Loader Indicator
                            if (isLoading) {
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { loadingProgress / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = Color(0xFF00C853),
                                    trackColor = Color.DarkGray
                                )
                            }
                        }
                    }
                }

                // WebView rendering Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )

                                // WebView Configurations
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.mediaPlaybackRequiresUserGesture = false
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.setSupportZoom(true)
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.cacheMode = WebSettings.LOAD_DEFAULT
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                                // Force GPU acceleration for media renders
                                setLayerType(View.LAYER_TYPE_HARDWARE, null)

                                webViewClient = object : WebViewClient() {
                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): WebResourceResponse? {
                                        val url = request?.url?.toString() ?: return null
                                        if (adBlockEnabled && AdBlocker.isAdUrl(url)) {
                                            scope.launch {
                                                blockedCount++
                                            }
                                            return WebResourceResponse(
                                                "text/plain",
                                                "UTF-8",
                                                java.io.ByteArrayInputStream(ByteArray(0))
                                            )
                                        }
                                        return super.shouldInterceptRequest(view, request)
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val url = request?.url?.toString() ?: return false

                                        // Block navigation redirects to ads
                                        if (adBlockEnabled && AdBlocker.isAdUrl(url)) {
                                            scope.launch {
                                                blockedCount++
                                            }
                                            return true // block this ad redirect load completely
                                        }

                                        if (url.startsWith("http://") || url.startsWith("https://")) {
                                            return false // Let WebView load content
                                        } else {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                ctx.startActivity(intent)
                                            } catch (e: Exception) {
                                                // ignore action failures
                                            }
                                            return true // block WebView from executing bad schemas
                                        }
                                    }

                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: Bitmap?
                                    ) {
                                        super.onPageStarted(view, url, favicon)
                                        isLoading = true
                                        loadingProgress = 0
                                        url?.let { currentUrl = it }
                                        
                                        // Update state indicators
                                        canGoBack = view?.canGoBack() == true
                                        canGoForward = view?.canGoForward() == true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoading = false
                                        url?.let { currentUrl = it }
                                        
                                        // Update state indicators
                                        canGoBack = view?.canGoBack() == true
                                        canGoForward = view?.canGoForward() == true

                                        // Inject JS popup blocker and cleaner
                                        if (adBlockEnabled) {
                                            view?.evaluateJavascript(AdBlocker.JS_BLOCK_INJECTION, null)
                                            
                                            val cssScript = """
                                                (function() {
                                                    var style = document.createElement('style');
                                                    style.type = 'text/css';
                                                    style.innerHTML = `${AdBlocker.CSS_BLOCK_STYLES}`;
                                                    document.head.appendChild(style);
                                                })();
                                            """.trimIndent()
                                            view?.evaluateJavascript(cssScript, null)
                                        }
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        super.onProgressChanged(view, newProgress)
                                        loadingProgress = newProgress
                                        if (newProgress >= 100) {
                                            isLoading = false
                                        }
                                    }

                                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                        super.onShowCustomView(view, callback)
                                        if (view != null && callback != null) {
                                            customView = view
                                            customViewCallback = callback
                                        }
                                    }

                                    override fun onHideCustomView() {
                                        super.onHideCustomView()
                                        customView = null
                                        customViewCallback = null
                                    }
                                }

                                loadUrl(currentUrl)
                                webViewRef = this
                            }
                        },
                        update = { webView ->
                            webViewRef = webView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Small Floating Activator when actions overhead is Hidden
            if (!showControls) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Card(
                        modifier = Modifier
                            .testTag("expand_overlay_button")
                            .clip(CircleShape)
                            .clickable { showControls = true },
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF16161A).copy(alpha = 0.85f)
                        ),
                        shape = CircleShape,
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Expand controls",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
