package cn.guajichi.idlepool.update;

import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

public final class UpdateChecker {
    public static final String PROJECT_URL = "https://github.com/SenalMC/IdlePool/";
    public static final String RELEASES_URL = PROJECT_URL + "releases";
    private static final String DEFAULT_VERSION_URL =
            "https://raw.githubusercontent.com/SenalMC/IdlePool/main/version.txt";

    private final JavaPlugin plugin;
    private final HttpClient client;
    private final AtomicReference<Result> result = new AtomicReference<>(Result.notChecked());

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void checkAsync() {
        if (!plugin.getConfig().getBoolean("update-check.enabled", true)) {
            result.set(new Result(State.DISABLED, "", "管理员已关闭更新检查"));
            return;
        }

        String url = plugin.getConfig().getString("update-check.version-url", DEFAULT_VERSION_URL);
        int timeoutSeconds = Math.max(2, plugin.getConfig().getInt("update-check.timeout-seconds", 5));
        result.set(new Result(State.CHECKING, "", "正在检查"));

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Accept", "text/plain")
                    .header("User-Agent", "IdlePool/" + currentVersion())
                    .GET()
                    .build();
        } catch (IllegalArgumentException exception) {
            fail("更新地址无效");
            return;
        }

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenAccept(this::acceptResponse)
                .exceptionally(failure -> {
                    Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                            ? failure.getCause()
                            : failure;
                    fail(readableError(cause));
                    return null;
                });
    }

    public Result result() {
        return result.get();
    }

    public String currentVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    private void acceptResponse(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            fail("GitHub 返回 HTTP " + response.statusCode());
            return;
        }
        String remoteVersion = response.body().lines().findFirst().orElse("").trim();
        if (remoteVersion.length() > 64 || !remoteVersion.matches("v?[0-9]+(?:\\.[0-9]+)*(?:-[0-9A-Za-z.-]+)?")) {
            fail("version.txt 中的版本号格式无效");
            return;
        }
        try {
            if (VersionComparator.compare(remoteVersion, currentVersion()) > 0) {
                Result update = new Result(State.UPDATE_AVAILABLE, remoteVersion, "检测到新版本");
                result.set(update);
                plugin.getLogger().warning("检测到 IdlePool 新版本 " + remoteVersion
                        + "，当前版本 " + currentVersion() + "。下载：" + RELEASES_URL);
            } else {
                result.set(new Result(State.UP_TO_DATE, remoteVersion, "已是最新版本"));
                plugin.getLogger().info("IdlePool 更新检查完成：当前已是最新版本（" + remoteVersion + "）。");
            }
        } catch (IllegalArgumentException exception) {
            fail(exception.getMessage());
        }
    }

    private void fail(String message) {
        result.set(new Result(State.ERROR, "", message));
        plugin.getLogger().warning("IdlePool 更新检查失败：" + message);
    }

    private static String readableError(Throwable failure) {
        String name = failure.getClass().getSimpleName();
        String message = failure.getMessage();
        return message == null || message.isBlank() ? name : name + "：" + message;
    }

    public enum State {
        NOT_CHECKED,
        DISABLED,
        CHECKING,
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        ERROR
    }

    public record Result(State state, String remoteVersion, String message) {
        private static Result notChecked() {
            return new Result(State.NOT_CHECKED, "", "尚未检查");
        }
    }
}
