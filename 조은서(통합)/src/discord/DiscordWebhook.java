package discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class DiscordWebhook {

    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".subalert");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.properties");
    private static final String KEY_WEBHOOK_URL = "discord.webhook.url";

    // 사용자 PC에 웹훅 URL 저장
    public static void saveWebhookUrl(String webhookUrl) throws Exception {
        if (!Files.exists(CONFIG_DIR)) {
            Files.createDirectories(CONFIG_DIR);
        }
        Properties props = new Properties();
        if (Files.exists(CONFIG_FILE)) {
            try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
                props.load(in);
            }
        }
        props.setProperty(KEY_WEBHOOK_URL, webhookUrl == null ? "" : webhookUrl.trim());
        try (OutputStream out = Files.newOutputStream(CONFIG_FILE)) {
            props.store(out, "Subscription Alert Settings");
        }
    }

    // 저장된 웹훅 URL 불러오기
    public static String loadWebhookUrl() {
        if (!Files.exists(CONFIG_FILE)) {
            return "";
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
            props.load(in);
            return props.getProperty(KEY_WEBHOOK_URL, "").trim();
        } catch (Exception e) {
            return "";
        }
    }

    // 결제 임박 알림 비동기 전송 (Notification.java에서 호출)
    public static void sendAlertAsync(String serviceName, String formattedPrice, String endDate, String cancelUrl) {
        String webhookUrl = loadWebhookUrl();
        if (webhookUrl.isEmpty()) {
            return;
        }

        new Thread(() -> {
            try {
                String content = "🔔 **" + serviceName + "** 결제일(" + endDate + ") 임박!";
                StringBuilder desc = new StringBuilder();
                desc.append("• **서비스명:** ").append(serviceName).append("\n");
                desc.append("• **결제 예정일:** ").append(endDate).append("\n");
                desc.append("• **결제 예정 금액:** ").append(formattedPrice).append("원\n");
                if (cancelUrl != null && !cancelUrl.isEmpty()) {
                    desc.append("• **해지 안내:** [🔗 해지 페이지 바로가기](").append(cancelUrl).append(")");
                }

                sendEmbed(webhookUrl, content, "🔔 구독 결제/마감 임박 알림", desc.toString(), 15158332);
                System.out.println("[디스코드 웹훅] 알림 전송 성공: " + serviceName);
            } catch (Exception e) {
                System.err.println("[디스코드 웹훅] 알림 전송 실패: " + e.getMessage());
            }
        }).start();
    }

    // [알림 설정] 탭의 테스트 알림 전송용
    public static String sendTestMessage(String webhookUrl) {
        String url = (webhookUrl == null) ? "" : webhookUrl.trim();
        if (url.isEmpty()) {
            return "❌ 디스코드 웹훅 URL을 먼저 입력해 주세요.";
        }
        if (!url.startsWith("https://discord.com/api/webhooks/")
                && !url.startsWith("https://discordapp.com/api/webhooks/")) {
            return "❌ 올바른 디스코드 웹훅 주소 형식이 아닙니다.\n(https://discord.com/api/webhooks/... 로 시작해야 합니다)";
        }

        try {
            sendEmbed(
                    url,
                    "🔔 구독 마감 알림 연동 테스트입니다!",
                    "✅ 디스코드 알림 연동 성공",
                    "이제 결제일이 임박하면 이 채널(및 스마트폰 푸시)로 해지 링크와 함께 알림이 도착합니다.",
                    3066993
            );
            return "✅ 테스트 알림을 보냈습니다! 디스코드 채널을 확인해 보세요.";
        } catch (Exception e) {
            return "❌ 테스트 알림 전송 실패: " + e.getMessage();
        }
    }

    private static void sendEmbed(String webhookUrl, String content, String title, String description, int color) throws Exception {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", title);
        embed.addProperty("description", description);
        embed.addProperty("color", color);

        JsonArray embeds = new JsonArray();
        embeds.add(embed);

        JsonObject payload = new JsonObject();
        payload.addProperty("username", "구독 마감 알림");
        payload.addProperty("content", content);
        payload.add("embeds", embeds);

        HttpURLConnection conn = (HttpURLConnection) new URL(webhookUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(7000);
        conn.setReadTimeout(7000);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("User-Agent", "SubscriptionAlertApp/1.0");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        conn.disconnect();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP 오류 코드 " + code + " (웹훅 URL이 삭제되었거나 잘못되었습니다)");
        }
    }
}