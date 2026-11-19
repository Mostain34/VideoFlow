package com.example.videoflow

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VideoFlowApp() }
    }
}

private enum class SourceType { YOUTUBE, FACEBOOK, DIRECT, INVALID }
private data class VideoInfo(
    val source: SourceType,
    val title: String,
    val creator: String = "",
    val thumbnail: String? = null,
    val embedHtml: String? = null,
    val detail: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoFlowApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var filename by remember { mutableStateOf("") }
    var permissionConfirmed by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<VideoInfo?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Facebook, YouTube অথবা সরাসরি অনুমোদিত ভিডিও URL পেস্ট করুন।") }

    MaterialTheme(colorScheme = if (android.os.Build.VERSION.SDK_INT >= 31) dynamicLightColorScheme(context) else lightColorScheme()) {
        Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("VideoFlow", fontWeight = FontWeight.Bold) }) }) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ElevatedCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Smart Video Link", style = MaterialTheme.typography.headlineSmall)
                        Text("লিংক পেস্ট করে Preview দেখুন", style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = url,
                            onValueChange = { value ->
                                url = value
                                info = null
                                if (value.contains("youtube.com", true) || value.contains("youtu.be", true) || value.contains("facebook.com", true) || value.contains("fb.watch", true)) {
                                    loading = true
                                    message = "লিংক শনাক্ত হয়েছে। Preview প্রস্তুত হচ্ছে…"
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) { fetchPreview(value.trim()) }
                                        info = result
                                        loading = false
                                        message = if (result == null) "Preview পাওয়া যায়নি। লিংকটি Public এবং সঠিক কি না যাচাই করুন।" else "লিংক শনাক্ত হয়েছে।"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Facebook / YouTube / Direct URL") },
                            leadingIcon = { Icon(Icons.Default.Link, null) },
                            singleLine = true
                        )
                        OutlinedTextField(value = filename, onValueChange = { filename = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ফাইলের নাম (ঐচ্ছিক)") }, singleLine = true)
                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        info?.let { video ->
                            HorizontalDivider()
                            Text(video.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (video.creator.isNotBlank()) Text(video.creator, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            video.thumbnail?.let { imageUrl ->
                                AsyncImage(model = imageUrl, contentDescription = "ভিডিও Thumbnail", modifier = Modifier.fillMaxWidth().heightIn(max = 210.dp))
                            }
                            if (video.source == SourceType.FACEBOOK && video.embedHtml != null) {
                                Text("Facebook Public Video Preview", style = MaterialTheme.typography.labelMedium)
                                AndroidView(
                                    factory = { ctx -> WebView(ctx).apply { settings.javaScriptEnabled = true; settings.domStorageEnabled = true; webViewClient = WebViewClient() } },
                                    update = { it.loadDataWithBaseURL("https://www.facebook.com/", video.embedHtml, "text/html", "UTF-8", null) },
                                    modifier = Modifier.fillMaxWidth().height(230.dp)
                                )
                            }
                            if (video.source == SourceType.YOUTUBE) {
                                Text("Metadata Preview পাওয়া গেছে। YouTube oEmbed ভিডিও ফাইল বা Download Stream দেয় না।", style = MaterialTheme.typography.bodySmall)
                            } else if (video.source == SourceType.FACEBOOK) {
                                Text("Meta oEmbed শুধু Embed তথ্য দেয়; এটি সরাসরি MP4 বা Quality তালিকা দেয় না।", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = permissionConfirmed, onCheckedChange = { permissionConfirmed = it })
                            Text("ভিডিওটি ডাউনলোড করার অনুমতি আমার আছে।")
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    message = withContext(Dispatchers.IO) { startVerifiedDownload(context, url.trim(), filename.trim(), permissionConfirmed) }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = url.isNotBlank() && !loading && (info == null || info?.source == SourceType.DIRECT)
                        ) {
                            Icon(Icons.Default.Download, null)
                            Spacer(Modifier.width(8.dp))
                            Text("ডাউনলোড শুরু")
                        }
                        if (info?.source == SourceType.YOUTUBE || info?.source == SourceType.FACEBOOK) {
                            Text("এই প্ল্যাটফর্ম লিংক থেকে অ্যাপ সরাসরি ভিডিও ফাইল নামাবে না। কেবল অনুমোদিত Direct Media URL ডাউনলোড করা যাবে।", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                ElevatedCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("স্ট্যাটাস", style = MaterialTheme.typography.titleMedium)
                        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Quality: Direct URL হলে সার্ভার যে ফাইল/স্ট্রিম দেয় সেটিই Original হিসেবে ডাউনলোড হবে। আলাদা 1080p/720p অপশন কেবল সোর্স API বৈধভাবে সরবরাহ করলে দেখানো সম্ভব।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun fetchPreview(raw: String): VideoInfo? {
    val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
    val host = uri.host?.lowercase() ?: return null
    return try {
        when {
            host == "youtu.be" || host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com") -> {
                val encoded = URLEncoder.encode(raw, "UTF-8")
                val json = getJson("https://www.youtube.com/oembed?url=$encoded&format=json")
                VideoInfo(SourceType.YOUTUBE, json.optString("title", "YouTube Video"), json.optString("author_name"), json.optString("thumbnail_url").takeIf { it.startsWith("https://") }, detail = "YouTube oEmbed")
            }
            host == "fb.watch" || host.endsWith("facebook.com") || host.endsWith("fb.com") -> {
                val encoded = URLEncoder.encode(raw, "UTF-8")
                val json = getJson("https://graph.facebook.com/v27.0/oembed_video?url=$encoded&omitscript=true")
                VideoInfo(SourceType.FACEBOOK, "Facebook Video", json.optString("provider_name", "Facebook"), embedHtml = json.optString("html").takeIf { it.isNotBlank() }, detail = "Meta oEmbed")
            }
            else -> VideoInfo(SourceType.DIRECT, "Direct media URL", detail = "Direct download")
        }
    } catch (_: Exception) { null }
}

private fun getJson(address: String): JSONObject {
    val connection = (URL(address).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 12000
        readTimeout = 12000
        setRequestProperty("User-Agent", "VideoFlow/1.1 (Android)")
    }
    try {
        if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
        return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
    } finally { connection.disconnect() }
}

private fun startVerifiedDownload(context: Context, rawUrl: String, requestedName: String, permissionConfirmed: Boolean): String {
    if (!permissionConfirmed) return "ডাউনলোডের অনুমতি নিশ্চিত করুন।"
    val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return "URL সঠিক নয়।"
    if ((uri.scheme != "https" && uri.scheme != "http") || uri.host.isNullOrBlank()) return "শুধু বৈধ HTTP/HTTPS URL গ্রহণযোগ্য।"
    val host = uri.host!!.lowercase()
    if (host == "youtu.be" || host.endsWith("youtube.com") || host.endsWith("facebook.com") || host == "fb.watch") return "এটি Platform Page URL, সরাসরি Media File URL নয়।"

    var mime: String? = null
    val check = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
        requestMethod = "HEAD"
        connectTimeout = 10000
        readTimeout = 10000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "VideoFlow/1.1 (Android)")
    }
    try {
        val code = check.responseCode
        if (code !in 200..299) return "সার্ভার ফাইল যাচাই করতে দেয়নি (HTTP $code)।"
        mime = check.contentType?.substringBefore(';')?.lowercase()
    } catch (e: Exception) {
        return "ফাইলের ধরন যাচাই করা যায়নি: ${e.message ?: "Network error"}"
    } finally { check.disconnect() }
    if (mime == null || !(mime!!.startsWith("video/") || mime == "application/octet-stream")) return "এটি ভিডিও ফাইল বলে নিশ্চিত হওয়া যায়নি (Content-Type: ${mime ?: "unknown"})। HTML/ওয়েবপেজ ডাউনলোড বন্ধ করা হয়েছে।"

    val ext = when (mime) { "video/mp4" -> ".mp4"; "video/webm" -> ".webm"; "video/quicktime" -> ".mov"; "video/x-matroska" -> ".mkv"; else -> ".video" }
    val rawName = requestedName.ifBlank { "VideoFlow_${System.currentTimeMillis()}$ext" }
    val cleaned = rawName.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(110)
    val safeName = if (cleaned.contains('.')) cleaned else cleaned + ext
    return try {
        val request = DownloadManager.Request(uri)
            .setTitle(safeName)
            .setDescription("VideoFlow verified media download")
            .setMimeType(mime)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, safeName)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        "ভিডিও ডাউনলোড কিউতে যোগ হয়েছে: $safeName ($mime)"
    } catch (e: Exception) { "ডাউনলোড শুরু করা যায়নি: ${e.message ?: "অজানা সমস্যা"}" }
}
