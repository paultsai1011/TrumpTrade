package com.paul.trumptrade

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Lang.en = Store(this).english
        Notifier.createChannel(this)
        schedule(this)
    }

    companion object {
        private const val WORK = "news_watch"
        fun schedule(ctx: Context) {
            val store = Store(ctx)
            val wm = WorkManager.getInstance(ctx)
            if (!store.notifyOn) {
                wm.cancelUniqueWork(WORK); return
            }
            val req = PeriodicWorkRequestBuilder<NewsWorker>(store.intervalMin.toLong(), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}

object Notifier {
    const val CH = "alerts"

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CH, "川普/政策快訊", NotificationManager.IMPORTANCE_HIGH)
        ch.description = "川普發言、行政命令與重大政策的即時通知"
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context, id: Int, title: String, text: String) {
        if (!canNotify(ctx)) return
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
        }
    }
}

/** 背景定時檢查川普新貼文與白宮/商務部新文件，符合關鍵字就推播（可附 AI 分析與相關台股） */
class NewsWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val store = Store(applicationContext)
        checkUpdate(store)
        if (!store.notifyOn) return Result.success()
        val res = try {
            Repo.fetch(Sources.alertSources, "")
        } catch (e: Exception) {
            return Result.retry()
        }
        if (res.items.isEmpty()) return Result.success()

        val seen = store.loadSeen()
        val seenSet = seen.toHashSet()
        val firstRun = seen.isEmpty()
        val fresh = res.items.filter { it.id !in seenSet }
        seen.addAll(fresh.map { it.id })
        store.saveSeen(seen)
        if (firstRun || fresh.isEmpty()) return Result.success()

        val kw = store.alertRegex()
        val hits = fresh.filter { item ->
            val text = item.title + " " + item.body
            (kw?.containsMatchIn(text) ?: false) || (item.source == "Truth Social" && item.themes.isNotEmpty()) ||
                    item.source == "白宮總統文件"
        }.sortedByDescending { it.time }.take(5)

        var aiUsed = 0
        for (item in hits) {
            var title = "${if (item.cat == Cat.TRUMP) tr("🇺🇸 川普", "🇺🇸 Trump") else tr("📜 政策", "📜 Policy")}｜${item.source}"
            var text = item.title.take(300)
            if (store.notifyAi && store.geminiKey.isNotBlank() && aiUsed < 2) {
                try {
                    val o = Gemini.ask(store, Prompts.news(item))
                    o.put("_t", System.currentTimeMillis())
                    store.saveAnalysis(item.id, o)
                    val a = parseAnalysis(o)
                    aiUsed++
                    if (a.importance <= 1) continue
                    title = "${"★".repeat(a.importance)} ${term(a.sentiment)}｜${item.source}"
                    val picks = a.stocks.joinToString(", ") { "${StockMap.label(it.code, it.name)} ${it.code}(${term(it.action)})" }
                    text = a.summary + if (picks.isNotBlank()) tr("\n相關台股：", "\nTaiwan stocks: ") + picks else ""
                } catch (_: Exception) {
                }
            } else if (item.themes.isNotEmpty()) {
                text += "\n題材：" + item.themes.joinToString("、")
            }
            Notifier.show(applicationContext, item.id.hashCode(), title, text)
        }
        return Result.success()
    }

    /** 每 6 小時檢查一次新版，有新版就通知 */
    private fun checkUpdate(store: Store) {
        val sp = applicationContext.getSharedPreferences("tt", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - sp.getLong("upd_check", 0L) < 6 * 3600_000L) return
        sp.edit().putLong("upd_check", now).apply()
        try {
            val info = Updater.check(applicationContext) ?: return
            if (sp.getInt("upd_notified", 0) >= info.version) return
            sp.edit().putInt("upd_notified", info.version).apply()
            Notifier.show(applicationContext, 9999, "📲 川普財經雷達有新版本 ${info.name}", "點我打開 App，按「立即更新」即可完成更新。")
        } catch (_: Exception) {
        }
    }
}
