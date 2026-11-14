package com.paul.trumptrade

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject

/** 檢查 GitHub Releases 有沒有新版；更新時交給瀏覽器下載（不需要額外權限） */
object Updater {
    private const val API = "https://api.github.com/repos/paultsai1011/TrumpTrade/releases/latest"
    const val APK_URL = "https://github.com/paultsai1011/TrumpTrade/releases/latest/download/TrumpTrade.apk"

    data class Info(val version: Int, val name: String, val notes: String, val url: String)

    fun currentVersion(ctx: Context): Int = try {
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0)).toInt()
    } catch (e: Exception) { 0 }

    fun currentName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (e: Exception) { "" }

    fun check(ctx: Context): Info? {
        val o = JSONObject(Net.get(API, mapOf("Accept" to "application/vnd.github+json")))
        val tag = o.s("tag_name")
        val ver = tag.substringAfterLast('.').toIntOrNull() ?: return null
        if (ver <= currentVersion(ctx)) return null
        return Info(ver, tag, o.s("body"), APK_URL)
    }

    fun openDownload(ctx: Context) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
