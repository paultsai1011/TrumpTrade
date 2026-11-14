package com.paul.trumptrade

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** App 內更新：從 GitHub Releases 檢查新版、下載 APK、叫出系統安裝畫面 */
object Updater {
    private const val API = "https://api.github.com/repos/paultsai1011/TrumpTrade/releases/latest"

    data class Info(val version: Int, val name: String, val notes: String, val url: String)

    fun currentVersion(ctx: Context): Int = try {
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0)).toInt()
    } catch (e: Exception) { 0 }

    fun currentName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (e: Exception) { "" }

    /** 有比目前新的版本就回傳，否則 null */
    fun check(ctx: Context): Info? {
        val o = JSONObject(Net.get(API, mapOf("Accept" to "application/vnd.github+json")))
        val tag = o.s("tag_name")
        val ver = tag.substringAfterLast('.').toIntOrNull() ?: return null
        if (ver <= currentVersion(ctx)) return null
        val assets = o.optJSONArray("assets") ?: return null
        var url = ""
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            if (a.s("name").endsWith(".apk")) { url = a.s("browser_download_url"); break }
        }
        if (url.isBlank()) return null
        return Info(ver, tag, o.s("body"), url)
    }

    private fun apkFile(ctx: Context, ver: Int): File {
        val dir = File(ctx.cacheDir, "update")
        dir.mkdirs()
        return File(dir, "TrumpTrade-$ver.apk")
    }

    fun downloaded(ctx: Context, info: Info): File? = apkFile(ctx, info.version).takeIf { it.exists() && it.length() > 100_000 }

    fun download(ctx: Context, info: Info, onProgress: (Int) -> Unit): File {
        val dir = File(ctx.cacheDir, "update")
        dir.listFiles()?.forEach { it.delete() }
        val out = apkFile(ctx, info.version)
        val tmp = File(dir, "dl.tmp")
        var url = info.url
        var c: HttpURLConnection
        var hops = 0
        while (true) {
            c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 20000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", Net.UA)
            val code = c.responseCode
            if (code in 300..399 && hops < 5) {
                url = c.getHeaderField("Location") ?: break
                c.disconnect(); hops++; continue
            }
            if (code !in 200..299) throw HttpException(code, "下載失敗")
            break
        }
        try {
            val total = c.contentLengthLong
            var done = 0L
            var lastPct = -1
            c.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        if (!tmp.renameTo(out)) throw IllegalStateException("儲存更新檔失敗")
        return out
    }

    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** 第一次需要允許「安裝未知應用程式」，會跳到設定頁 */
    fun openInstallPermission(ctx: Context) {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}
