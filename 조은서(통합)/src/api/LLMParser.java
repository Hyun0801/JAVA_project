package api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import database.SubscriptionDAO;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

public class LLMParser {

    private static final String API_KEY = getApiKey();
    private static final String API_URL = "https://llm.deepaibit.com/v1/chat/completions";

    // DB에 새 구독이 추가되었을 때 Swing UI를 자동 갱신하기 위한 콜백
    private static Runnable onDataUpdated;

    public static void setOnDataUpdated(Runnable callback) {
        onDataUpdated = callback;
    }

    private static String getApiKey() {
        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            String key = dotenv.get("API_KEY");
            return key != null ? key.trim() : "";
        } catch (Exception e) {
            System.out.println("보안 키(.env) 파일을 찾을 수 없습니다.");
            return "";
        }
    }

    /**
     * 자연어 문장을 LLM으로 파싱해 SQLite DB에 저장하고, 결과 안내 메시지를 반환합니다.
     * Swing 버튼과 디스코드 챗봇 양쪽에서 이 메서드 하나만 호출하면 됩니다.
     */
    public static String parseAndSave(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return "❌ 입력된 문장이 비어 있습니다.";
        }
        if (API_KEY.isEmpty()) {
            return "❌ .env 파일에서 API_KEY를 찾을 수 없습니다.";
        }

        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);

            String today = LocalDate.now().toString();
            String prompt = "오늘 날짜는 " + today + "이다. "
                    + "다음 문장에서 구독 서비스명(service), 월 요금 숫자(price), 다음 결제/종료일(date, yyyy-MM-dd 형식)을 추출해줘. "
                    + "'다음 달 5일', '3일 뒤' 같은 상대적인 날짜는 오늘 날짜(" + today + ")를 기준으로 정확히 계산해. "
                    + "설명이나 마크다운 코드블록 없이 오직 JSON 객체만 출력해. "
                    + "예시: {\"service\": \"넷플릭스 프리미엄\", \"price\": 17000, \"date\": \"2026-12-05\"}\n"
                    + "분석할 문장: " + userInput;

            // Gson으로 안전하게 JSON 요청 본문 생성
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", "qwen-27b");
            requestBody.addProperty("temperature", 0);

            JsonArray messages = new JsonArray();
            JsonObject sysMsg = new JsonObject();
            sysMsg.addProperty("role", "system");
            sysMsg.addProperty("content", "너는 구독 정보 데이터 추출 전문가야. 반드시 순수 JSON만 응답해.");
            messages.add(sysMsg);

            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", prompt);
            messages.add(userMsg);

            requestBody.add("messages", messages);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            InputStream stream = (responseCode >= 200 && responseCode < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            StringBuilder response = new StringBuilder();
            if (stream != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line.trim());
                    }
                }
            }

            System.out.println("서버 응답 코드: " + responseCode);
            System.out.println("서버 상세 응답: " + response);

            if (responseCode < 200 || responseCode >= 300) {
                return "❌ LLM API 통신 오류가 발생했습니다. (응답 코드: " + responseCode + ")";
            }

            // OpenAI 호환 응답 구조에서 content 추출
            JsonObject root = JsonParser.parseString(response.toString()).getAsJsonObject();
            String content = root.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();

            // 혹시 모를 ```json ... ``` 태그 및 앞뒤 텍스트 제거
            content = content.replace("```json", "").replace("```", "").trim();
            int startIdx = content.indexOf("{");
            int endIdx = content.lastIndexOf("}");
            if (startIdx != -1 && endIdx != -1) {
                content = content.substring(startIdx, endIdx + 1);
            }

            JsonObject parsed = JsonParser.parseString(content).getAsJsonObject();
            String extractedService = parsed.get("service").getAsString().trim();
            String priceDigits = parsed.get("price").getAsString().replaceAll("[^0-9]", "");
            int extractedPrice = Integer.parseInt(priceDigits);
            String extractedDate = LocalDate.parse(parsed.get("date").getAsString().trim()).toString();

            String cancelUrl = getCancelUrl(extractedService);

            // SQLite DB 저장
            SubscriptionDAO.insertSubscription(extractedService, extractedPrice, extractedDate, cancelUrl);

            // Swing 화면이 열려 있다면 즉시 표 갱신
            if (onDataUpdated != null) {
                onDataUpdated.run();
            }

            return String.format(
                    "✅ 구독 등록 완료!\n- 서비스명: %s\n- 월 요금: %,d원\n- 결제/종료일: %s\n- 해지 링크: %s",
                    extractedService, extractedPrice, extractedDate, cancelUrl
            );

        } catch (Exception e) {
            e.printStackTrace();
            return "❌ 문장을 분석하거나 저장하는 중 오류가 발생했습니다.\n(예: \"넷플릭스 프리미엄 17000원 다음 달 5일 결제\"처럼 입력해 보세요.)";
        }
    }

    /**
     * 디스코드 챗봇에 보낼 월별 지출 요약 메시지를 생성합니다.
     */
    public static String getMonthlySummary() {
        List<String[]> list = SubscriptionDAO.getAllSubscriptions();
        if (list.isEmpty()) {
            return "현재 등록된 구독 서비스가 없습니다.";
        }

        StringBuilder sb = new StringBuilder("📊 [구독 지출 현황 요약]\n");
        int total = 0;
        for (String[] sub : list) {
            int price = Integer.parseInt(sub[2]);
            total += price;
            sb.append(String.format("- %s: %,d원 (결제/종료일: %s)\n", sub[0], price, sub[1]));
        }
        sb.append(String.format("--------------------\n💰 월 총 지출액: %,d원", total));
        return sb.toString();
    }

    /**
     * 주요 서비스는 공식 해지/계정 링크를 반환하고, 그 외는 구글 검색 링크를 반환합니다.
     */
    public static String getCancelUrl(String serviceName) {
        String lower = serviceName.toLowerCase();
        if (lower.contains("netflix") || lower.contains("넷플릭스")) {
            return "https://www.netflix.com/cancelplan";
        } else if (lower.contains("youtube") || lower.contains("유튜브")) {
            return "https://www.youtube.com/paid_memberships";
        } else if (lower.contains("tving") || lower.contains("티빙")) {
            return "https://www.tving.com/my/pass";
        } else if (lower.contains("disney") || lower.contains("디즈니")) {
            return "https://www.disneyplus.com/account";
        } else if (lower.contains("adobe") || lower.contains("어도비")) {
            return "https://account.adobe.com/plans";
        } else if (lower.contains("coupang") || lower.contains("쿠팡")) {
            return "https://www.coupang.com/";
        }
        String encoded = URLEncoder.encode(serviceName + " 해지", StandardCharsets.UTF_8);
        return "https://www.google.com/search?q=" + encoded;
    }
}