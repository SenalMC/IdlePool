package top.cnuo.idlepool.update

import org.bukkit.plugin.java.JavaPlugin
import top.cnuo.idlepool.util.Messages
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicReference

class UpdateChecker(private val plugin: JavaPlugin) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build()
    private val current = AtomicReference(Result(State.NOT_CHECKED, "", ""))

    fun checkAsync() {
        if (!plugin.config.getBoolean("update-check.enabled", true)) { current.set(Result(State.DISABLED, "", Messages.raw("update.disabled"))); return }
        val timeout = plugin.config.getInt("update-check.timeout-seconds", 5).coerceAtLeast(2)
        val url = plugin.config.getString("update-check.version-url", DEFAULT_VERSION_URL) ?: DEFAULT_VERSION_URL
        current.set(Result(State.CHECKING, "", Messages.raw("update.checking")))
        val request = try { HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(timeout.toLong())).header("Accept", "text/plain").header("User-Agent", "IdlePool/${currentVersion()}").GET().build() }
            catch (_: IllegalArgumentException) { fail(Messages.raw("update.invalid-url")); return }
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenAccept(::accept).exceptionally {
            val cause = if (it is CompletionException && it.cause != null) it.cause!! else it
            fail(Messages.raw("update.request-failed", mapOf("error" to (cause.message ?: cause.javaClass.simpleName)))); null
        }
    }
    fun result() = current.get()
    fun currentVersion() = plugin.pluginMeta.version

    private fun accept(response: HttpResponse<String>) {
        if (response.statusCode() != 200) { fail(Messages.raw("update.http-error", mapOf("status" to response.statusCode().toString()))); return }
        val remote = response.body().lineSequence().firstOrNull()?.trim().orEmpty()
        if (remote.length > 64 || !remote.matches(Regex("v?[0-9]+(?:\\.[0-9]+)*(?:-[0-9A-Za-z.-]+)?"))) { fail(Messages.raw("update.invalid-version")); return }
        try {
            if (VersionComparator.compare(remote, currentVersion()) > 0) {
                current.set(Result(State.UPDATE_AVAILABLE, remote, Messages.raw("update.available")))
                plugin.logger.warning("检测到 IdlePool 新版本 $remote，当前版本 ${currentVersion()}。下载：$RELEASES_URL")
            } else { current.set(Result(State.UP_TO_DATE, remote, Messages.raw("update.up-to-date"))); plugin.logger.info("IdlePool 更新检查完成：当前已是最新版本（$remote）。") }
        } catch (exception: IllegalArgumentException) { fail(Messages.raw("update.compare-failed", mapOf("error" to exception.message.orEmpty()))) }
    }
    private fun fail(message: String) { current.set(Result(State.ERROR, "", message)); plugin.logger.warning("IdlePool 更新检查失败：$message") }
    enum class State { NOT_CHECKED, DISABLED, CHECKING, UP_TO_DATE, UPDATE_AVAILABLE, ERROR }
    data class Result(val state: State, val remoteVersion: String, val message: String) {
        fun state() = state; fun remoteVersion() = remoteVersion; fun message() = message
    }
    companion object {
        const val PROJECT_URL = "https://github.com/SenalMC/IdlePool/"
        const val RELEASES_URL = "${PROJECT_URL}releases"
        private const val DEFAULT_VERSION_URL = "https://raw.githubusercontent.com/SenalMC/IdlePool/main/version.txt"
    }
}
