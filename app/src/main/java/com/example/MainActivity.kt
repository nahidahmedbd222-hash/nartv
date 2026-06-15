package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled", "CollectionSize")
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Persistent Settings
    val appSettings = remember { AppSettings(context) }

    // State Variables bound to settings
    var adBlockEnabled by remember { mutableStateOf(appSettings.adBlockEnabled) }
    var desktopModeEnabled by remember { mutableStateOf(appSettings.desktopModeEnabled) }
    var customBlockedDomains by remember { mutableStateOf(appSettings.customBlockedDomains) }
    var themeAccentIndex by remember { mutableStateOf(appSettings.themeAccentIndex) }

    // Active block tracker
    var blockedCount by remember { mutableStateOf(0) }
    val blockedHistory = remember { mutableStateListOf<String>() }

    // Theme values (Green, Blue, Amber, Magenta)
    val accentColors = listOf(
        Color(0xFF00C853), // Green
        Color(0xFF00B0FF), // Blue
        Color(0xFFFFAB00), // Amber
        Color(0xFFFF4081)  // Magenta
    )
    val activeAccentColor = accentColors.getOrElse(themeAccentIndex) { Color(0xFF00C853) }

    // WebView States
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("https://tv.rootitsystem.com/") }
    var isLoading by remember { mutableStateOf(true) }
    var loadingProgress by remember { mutableStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    // Navigation and Popup Controllers
    var showControls by remember { mutableStateOf(true) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    // Full screen video support
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // Intercept hardware Back Button
    BackHandler(enabled = true) {
        if (customView != null) {
            customViewCallback?.onCustomViewHidden()
            customView = null
            customViewCallback = null
        } else if (showSettingsSheet) {
            showSettingsSheet = false
        } else if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            (context as? ComponentActivity)?.finish()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070709))
    ) {
        if (customView != null) {
            // Fullscreen custom layout for video players
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
            // Standard stream view & overlays
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Immersive Header Card (Actions & Brand bar)
                AnimatedVisibility(
                    visible = showControls,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("controls_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF111115).copy(alpha = 0.95f)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            // Row 1: Logo Icon (Nar Logo), App Title, and AdShield Status Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Brand logo (nar logo) or fallback icon
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(
                                                        activeAccentColor.copy(alpha = 0.3f),
                                                        Color.Transparent
                                                    )
                                                )
                                            )
                                            .border(
                                                1.5.dp,
                                                activeAccentColor.copy(alpha = 0.6f),
                                                RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Load launcher tv logo, or fallback nicely
                                        val brandingPainter = painterResource(id = R.drawable.tv_icon)
                                        Image(
                                            painter = brandingPainter,
                                            contentDescription = "Nar TV Logo Icon",
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Root IT TV",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 16.sp,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        activeAccentColor.copy(alpha = 0.12f),
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = "PRO",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = activeAccentColor
                                                )
                                            }
                                        }
                                        Text(
                                            text = currentUrl.replace("https://", "").replace("http://", ""),
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = Color.Gray
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // Shields Status Indicator Badge (clickable shortcut)
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (adBlockEnabled) activeAccentColor.copy(alpha = 0.12f)
                                            else Color(0xFFFF4D4D).copy(alpha = 0.12f)
                                        )
                                        .clickable {
                                            adBlockEnabled = !adBlockEnabled
                                            appSettings.adBlockEnabled = adBlockEnabled
                                            webViewRef?.reload()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (adBlockEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = "Shield Active State Badge",
                                        tint = if (adBlockEnabled) activeAccentColor else Color(0xFFFF4D4D),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (adBlockEnabled) "$blockedCount Blocked" else "Shields Low",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = if (adBlockEnabled) activeAccentColor else Color(0xFFFF4D4D)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Quick Hide topbar button
                                IconButton(
                                    onClick = { showControls = false },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss status bar overlay",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Row 2: Navigation controls, Home, Refresh, Settings Activator
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Navigate Back
                                    IconButton(
                                        onClick = { webViewRef?.goBack() },
                                        enabled = canGoBack,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .background(
                                                if (canGoBack) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Navigate back",
                                            tint = if (canGoBack) Color.White else Color.DarkGray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Navigate Forward
                                    IconButton(
                                        onClick = { webViewRef?.goForward() },
                                        enabled = canGoForward,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .background(
                                                if (canGoForward) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Navigate forward",
                                            tint = if (canGoForward) Color.White else Color.DarkGray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Go Home
                                    IconButton(
                                        onClick = { webViewRef?.loadUrl("https://tv.rootitsystem.com/") },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Home,
                                            contentDescription = "Reset stream home",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Refresh Stream
                                    IconButton(
                                        onClick = { webViewRef?.reload() },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Refresh streaming content",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Interactive Cog Settings Toggle Button
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (desktopModeEnabled) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    activeAccentColor.copy(alpha = 0.15f),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "DESKTOP",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = activeAccentColor
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = { showSettingsSheet = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White.copy(alpha = 0.1f),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        modifier = Modifier
                                            .height(36.dp)
                                            .testTag("activate_settings_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Settings config Panel",
                                            modifier = Modifier.size(16.dp),
                                            tint = activeAccentColor
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Settings",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Horizontal Dynamic Loading Progress Indicator
                            if (isLoading) {
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { loadingProgress / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = activeAccentColor,
                                    trackColor = Color.DarkGray
                                )
                            }
                        }
                    }
                }

                // Primary Web View Stream Renderer Content Frame
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

                                // Desktop vs Mobile User Agent
                                if (desktopModeEnabled) {
                                    settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                }

                                // Hardware GPU settings
                                setLayerType(View.LAYER_TYPE_HARDWARE, null)

                                webViewClient = object : WebViewClient() {
                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): WebResourceResponse? {
                                        val url = request?.url?.toString() ?: return null
                                        if (adBlockEnabled && AdBlocker.isAdUrl(url, customBlockedDomains)) {
                                            val host = request.url?.host ?: "Ad network domain"
                                            scope.launch {
                                                blockedCount++
                                                if (!blockedHistory.contains(host)) {
                                                    if (blockedHistory.size >= 12) {
                                                        blockedHistory.removeAt(0)
                                                    }
                                                    blockedHistory.add(host)
                                                }
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

                                        // Block redirect popup ads
                                        if (adBlockEnabled && AdBlocker.isAdUrl(url, customBlockedDomains)) {
                                            val host = request.url?.host ?: "Ad redirect"
                                            scope.launch {
                                                blockedCount++
                                                if (!blockedHistory.contains(host)) {
                                                    if (blockedHistory.size >= 12) {
                                                        blockedHistory.removeAt(0)
                                                    }
                                                    blockedHistory.add(host)
                                                }
                                            }
                                            return true // block execution
                                        }

                                        if (url.startsWith("http://") || url.startsWith("https://")) {
                                            return false // Let WebView render
                                        } else {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                ctx.startActivity(intent)
                                            } catch (e: Exception) {
                                                // safe catch schema errors
                                            }
                                            return true
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
                                        canGoBack = view?.canGoBack() == true
                                        canGoForward = view?.canGoForward() == true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoading = false
                                        url?.let { currentUrl = it }
                                        canGoBack = view?.canGoBack() == true
                                        canGoForward = view?.canGoForward() == true

                                        // Inject JS blocker scripts
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

            // Small Floating Activator when overlay actions are hidden
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
                            containerColor = Color(0xFF111115).copy(alpha = 0.88f)
                        ),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
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

        // Custom Bottom Sheet style Modal Overlay for Advanced Settings
        if (showSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false },
                containerColor = Color(0xFF111115),
                scrimColor = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                // Settings UI Core Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                ) {
                    // Header Title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Config Icon",
                                tint = activeAccentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Shield & Stream Settings",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                        IconButton(
                            onClick = { showSettingsSheet = false },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close sheet button",
                                tint = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Lazy List of Setting groups
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Switch 1: AdBlock Toggling
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Built-in AdShield Blocker",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Block intrusive dynamic overlays, trackers, and popup redirects.",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                Switch(
                                    checked = adBlockEnabled,
                                    onCheckedChange = {
                                        adBlockEnabled = it
                                        appSettings.adBlockEnabled = it
                                        webViewRef?.reload()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = activeAccentColor,
                                        checkedTrackColor = activeAccentColor.copy(alpha = 0.4f),
                                        uncheckedThumbColor = Color.LightGray,
                                        uncheckedTrackColor = Color.DarkGray
                                    )
                                )
                            }
                        }

                        // Switch 2: Desktop Mode Toggling
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Force Desktop Mode UA",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Request standard high definition desktop player streams.",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                Switch(
                                    checked = desktopModeEnabled,
                                    onCheckedChange = {
                                        desktopModeEnabled = it
                                        appSettings.desktopModeEnabled = it
                                        // Update WebView instance directly
                                        webViewRef?.let { webView ->
                                            if (it) {
                                                webView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                            } else {
                                                webView.settings.userAgentString = null
                                            }
                                            webView.reload()
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = activeAccentColor,
                                        checkedTrackColor = activeAccentColor.copy(alpha = 0.4f),
                                        uncheckedThumbColor = Color.LightGray,
                                        uncheckedTrackColor = Color.DarkGray
                                    )
                                )
                            }
                        }

                        // Section 3: Accent theme color setting
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "Branding Glow Theme Color",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    accentColors.forEachIndexed { idx, color ->
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .border(
                                                    width = if (themeAccentIndex == idx) 2.5.dp else 0.dp,
                                                    color = Color.White,
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    themeAccentIndex = idx
                                                    appSettings.themeAccentIndex = idx
                                                }
                                        )
                                    }
                                }
                            }
                        }

                        // Section 4: Custom Block Rules
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "Custom Ad-Domain Filtering",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Prevent loading items from special third-party servers.",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Input form
                                var newDomainText by remember { mutableStateOf("") }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = newDomainText,
                                        onValueChange = { newDomainText = it },
                                        placeholder = { Text("ads.adserver.xxx", fontSize = 12.sp, color = Color.Gray) },
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = activeAccentColor,
                                            unfocusedBorderColor = Color.DarkGray,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.LightGray
                                        ),
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    Button(
                                        onClick = {
                                            if (newDomainText.trim().isNotEmpty()) {
                                                appSettings.addCustomDomain(newDomainText.trim())
                                                customBlockedDomains = appSettings.customBlockedDomains
                                                newDomainText = ""
                                                Toast.makeText(context, "Added Block Rule!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = activeAccentColor
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                    }
                                }

                                if (customBlockedDomains.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Your custom rules:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.LightGray
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        customBlockedDomains.forEach { domain ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = domain,
                                                    fontSize = 11.sp,
                                                    color = Color.LightGray
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete custom block rule",
                                                    tint = Color(0xFFFF5252),
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clickable {
                                                            appSettings.removeCustomDomain(domain)
                                                            customBlockedDomains = appSettings.customBlockedDomains
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Section 5: Real-time Block Logs
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Shield Block Logs",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Recent domains blocked on this page.",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    if (blockedHistory.isNotEmpty()) {
                                        Text(
                                            text = "Clear list",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = activeAccentColor,
                                            modifier = Modifier.clickable {
                                                blockedHistory.clear()
                                                blockedCount = 0
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (blockedHistory.isEmpty()) {
                                    Text(
                                        text = "No blocked networks recorded yet. Happy streaming!",
                                        fontSize = 11.sp,
                                        color = Color.DarkGray,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                } else {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        blockedHistory.reversed().forEach { host ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(vertical = 2.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFFF5252))
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = host,
                                                    fontSize = 11.sp,
                                                    color = Color.LightGray,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Section 6: Cache Maintenance Action
                        item {
                            Button(
                                onClick = {
                                    webViewRef?.let { webView ->
                                        webView.clearCache(true)
                                        webView.clearHistory()
                                        val cookieManager = CookieManager.getInstance()
                                        cookieManager.removeAllCookies(null)
                                        cookieManager.flush()
                                        Toast.makeText(context, "Cookies and Cache Cleared successfully!", Toast.LENGTH_LONG).show()
                                        webView.reload()
                                        showSettingsSheet = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF5252).copy(alpha = 0.12f),
                                    contentColor = Color(0xFFFF5252)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.25f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sweep Cache icon",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Wipe Cookies & Web Cache",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
