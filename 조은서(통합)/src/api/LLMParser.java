package api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.time.YearMonth;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LLMParser {

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

    private static class ParsedResult {
        String service;
        int price;
        String date; // yyyy-MM-dd
    }

    /**
     * 자연어 문장을 LLM으로 파싱해 SQLite DB에 저장하고 결과 안내 메시지를 반환합니다.
     * - 예시와 다른 자유로운 문장/어순도 처리합니다.
     * - LLM 응답 오류 또는 네트워크 장애 시 내장 자연어 분석기로 자동 보완합니다.
     */
    public static String parseAndSave(String userInput) {
        if (isBlank(userInput)) {
            return "❌ 입력된 문장이 비어 있습니다.";
        }

        String text = userInput.trim();
        ParsedResult result = null;
        boolean usedFallback = false;

        // 1차 시도: 기존 deepaibit LLM API (qwen-27b)
        String apiKey = getApiKey();
        if (!apiKey.isEmpty()) {
            try {
                result = parseWithDeepAiBit(text, apiKey);
            } catch (Exception e) {
                System.err.println("[LLMParser] LLM API 파싱 실패 -> 자체 분석기로 전환: " + e.getMessage());
            }
        } else {
            System.out.println("[LLMParser] API_KEY가 없어 자체 자연어 분석기로 처리합니다.");
        }

        // 2차 보완(Fallback): API 실패 또는 필수값 누락 시 내장 자연어 분석기로 추출
        ParsedResult local = parseLocally(text);
        if (result == null) {
            result = local;
            usedFallback = true;
        } else {
            if (isBlank(result.service)) result.service = local.service;
            if (!isValidDate(result.date)) result.date = local.date;
            if (result.price <= 0 && local.price > 0) result.price = local.price;
        }

        // 최종 검증: 어떤 정보가 빠졌는지 구체적으로 안내
        if (isBlank(result.service)) {
            return "❌ 서비스명을 찾지 못했습니다.\n(예: \"넷플릭스 17000원 다음 달 3일\", \"12/5 유튜브 프리미엄 14900\")";
        }
        if (!isValidDate(result.date)) {
            return "❌ 결제/종료일을 찾지 못했습니다.\n(예: \"다음 달 3일\", \"12월 5일\", \"2026-12-03\", \"3일 뒤\", \"매달 15일\")";
        }

        try {
            String cancelUrl = getCancelUrl(result.service);

            // SQLite DB 저장
            SubscriptionDAO.insertSubscription(result.service, result.price, result.date, cancelUrl);

            // Swing 화면이 열려 있다면 즉시 표 갱신
            if (onDataUpdated != null) {
                onDataUpdated.run();
            }

            String tag = usedFallback ? "자동 분석" : "AI 분석";
            return String.format(
                    "✅ 구독 등록 완료! (%s)\n- 서비스명: %s\n- 월 요금: %,d원\n- 결제/종료일: %s\n- 해지 링크: %s",
                    tag, result.service, result.price, result.date, cancelUrl
            );
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ DB 저장 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

    // ==================== 1. deepaibit LLM API 호출 ====================

    private static ParsedResult parseWithDeepAiBit(String userInput, String apiKey) throws Exception {
        URL url = new URL(API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);

        String today = LocalDate.now().toString();
        String prompt = "오늘 날짜는 " + today + "이다.\n"
                + "아래 문장은 어순이나 형식이 자유로운 한국어 문장이다. 문장에서 아래 3가지를 추출해 순수 JSON 객체 1개만 출력하라.\n"
                + "1. service: 구독 서비스명 문자열 (요금제명 포함 가능)\n"
                + "2. price: 월 요금 정수(원 단위 숫자만, 무료 체험이면 0, '1.7만원'은 17000)\n"
                + "3. date: 다음 결제일 또는 종료일 (반드시 두 자리 월/일 yyyy-MM-dd 형식)\n"
                + "규칙:\n"
                + "- '다음 달 3일', '3일 뒤', '매달 15일', '12/5', '내일' 등 상대 날짜는 오늘(" + today + ") 기준으로 정확히 계산하라.\n"
                + "- <think> 태그, 설명, 마크다운 코드블록 없이 오직 JSON만 출력하라.\n"
                + "예시 출력: {\"service\": \"넷플릭스 프리미엄\", \"price\": 17000, \"date\": \"2026-12-03\"}\n"
                + "분석할 문장: " + userInput;

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "qwen-27b");
        requestBody.addProperty("temperature", 0);

        JsonArray messages = new JsonArray();
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", "너는 구독 정보 추출기다. 반드시 {\"service\":..., \"price\":..., \"date\":...} 형식의 순수 JSON 객체만 출력한다.");
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
                    response.append(line);
                }
            }
        }
        conn.disconnect();

        System.out.println("서버 응답 코드: " + responseCode);
        System.out.println("서버 상세 응답: " + response);

        if (responseCode < 200 || responseCode >= 300) {
            throw new IllegalStateException("HTTP " + responseCode);
        }

        JsonObject root = JsonParser.parseString(response.toString()).getAsJsonObject();
        JsonObject msgObj = root.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message");

        String content = (!msgObj.has("content") || msgObj.get("content").isJsonNull())
                ? "" : msgObj.get("content").getAsString();

        // qwen 등 추론 모델의 <think>...</think> 블록 및 코드블록 제거
        content = Pattern.compile("(?s)<think>.*?</think>").matcher(content).replaceAll("");
        content = content.replace("```json", "").replace("```", "").trim();

        JsonObject parsed = extractValidJsonObject(content);
        if (parsed == null) {
            throw new IllegalStateException("유효한 JSON 객체를 찾지 못했습니다: " + content);
        }

        ParsedResult res = new ParsedResult();
        res.service = firstNonEmpty(
                getStringSafe(parsed, "service"),
                getStringSafe(parsed, "serviceName"),
                getStringSafe(parsed, "name")
        );
        res.price = getIntSafe(parsed, "price", "amount", "cost");
        res.date = normalizeDate(firstNonEmpty(
                getStringSafe(parsed, "date"),
                getStringSafe(parsed, "endDate"),
                getStringSafe(parsed, "paymentDate")
        ));

        return res;
    }

    // ==================== 2. 내장 스마트 자연어 분석기 (어순 무관) ====================

    private static ParsedResult parseLocally(String input) {
        ParsedResult res = new ParsedResult();
        LocalDate today = LocalDate.now();
        String work = " " + input + " ";

        // [1단계] 날짜 추출 후 문장에서 제거
        LocalDate date = null;
        String matchedDate = null;

        // 1) yyyy-MM-dd / yyyy.M.d / yyyy/M/d / yyyy년 M월 d일
        Matcher m = Pattern.compile("(\\d{4})\\s*[-./년]\\s*(\\d{1,2})\\s*[-./월]\\s*(\\d{1,2})\\s*일?").matcher(work);
        if (m.find()) {
            date = safeDate(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            matchedDate = m.group(0);
        }

        // 2) 다음 달 N일 / 내달 N일 / 익월 N일
        if (date == null) {
            m = Pattern.compile("(다음\\s*달|내달|익월)\\s*(\\d{1,2})\\s*일?").matcher(work);
            if (m.find()) {
                YearMonth ym = YearMonth.from(today).plusMonths(1);
                date = safeDate(ym.getYear(), ym.getMonthValue(), Integer.parseInt(m.group(2)));
                matchedDate = m.group(0);
            }
        }

        // 3) 이번 달 N일 / 이달 N일
        if (date == null) {
            m = Pattern.compile("(이번\\s*달|이달|당월)\\s*(\\d{1,2})\\s*일?").matcher(work);
            if (m.find()) {
                YearMonth ym = YearMonth.from(today);
                date = safeDate(ym.getYear(), ym.getMonthValue(), Integer.parseInt(m.group(2)));
                matchedDate = m.group(0);
            }
        }

        // 4) 매달 N일 / 매월 N일
        if (date == null) {
            m = Pattern.compile("(매달|매월|매\\s*달|매\\s*월)\\s*(\\d{1,2})\\s*일?").matcher(work);
            if (m.find()) {
                date = nextOccurrenceOfDay(today, Integer.parseInt(m.group(2)));
                matchedDate = m.group(0);
            }
        }

        // 5) N일 뒤 / N일 후 / N일 남음
        if (date == null) {
            m = Pattern.compile("(\\d{1,3})\\s*일\\s*(뒤|후|남음|남았|있다가)").matcher(work);
            if (m.find()) {
                date = today.plusDays(Integer.parseInt(m.group(1)));
                matchedDate = m.group(0);
            }
        }

        // 6) 일주일 뒤 / N주 뒤 / 한 달 뒤 / N개월 뒤
        if (date == null) {
            m = Pattern.compile("(일주일|한\\s*주|(\\d{1,2})\\s*주일?)\\s*(뒤|후|남음)").matcher(work);
            if (m.find()) {
                int weeks = (m.group(2) != null) ? Integer.parseInt(m.group(2)) : 1;
                date = today.plusWeeks(weeks);
                matchedDate = m.group(0);
            }
        }
        if (date == null) {
            m = Pattern.compile("(한\\s*달|(\\d{1,2})\\s*(개월|달))\\s*(뒤|후|남음)").matcher(work);
            if (m.find()) {
                int months = (m.group(2) != null) ? Integer.parseInt(m.group(2)) : 1;
                date = today.plusMonths(months);
                matchedDate = m.group(0);
            }
        }

        // 7) 오늘 / 내일 / 모레 / 글피
        if (date == null) {
            m = Pattern.compile("(오늘|내일|모레|글피)").matcher(work);
            if (m.find()) {
                String kw = m.group(1);
                if (kw.equals("오늘")) date = today;
                else if (kw.equals("내일")) date = today.plusDays(1);
                else if (kw.equals("모레")) date = today.plusDays(2);
                else date = today.plusDays(3);
                matchedDate = m.group(0);
            }
        }

        // 8) M월 d일
        if (date == null) {
            m = Pattern.compile("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일?").matcher(work);
            if (m.find()) {
                date = nextOccurrenceOfMonthDay(today, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
                matchedDate = m.group(0);
            }
        }

        // 9) M/d 또는 M.d (1.7만원 같은 소수 금액 제외)
        if (date == null) {
            m = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*[/.-]\\s*(\\d{1,2})(?!\\s*[만천백원\\d])").matcher(work);
            if (m.find()) {
                int mo = Integer.parseInt(m.group(1));
                int dy = Integer.parseInt(m.group(2));
                if (mo >= 1 && mo <= 12 && dy >= 1 && dy <= 31) {
                    date = nextOccurrenceOfMonthDay(today, mo, dy);
                    matchedDate = m.group(0);
                }
            }
        }

        // 10) 단독 N일 (예: "넷플릭스 17000원 5일 결제")
        if (date == null) {
            m = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*일(?!\\s*[만천백원])").matcher(work);
            if (m.find()) {
                int dy = Integer.parseInt(m.group(1));
                if (dy >= 1 && dy <= 31) {
                    date = nextOccurrenceOfDay(today, dy);
                    matchedDate = m.group(0);
                }
            }
        }

        if (date != null) {
            res.date = date.toString();
            work = work.replaceFirst(Pattern.quote(matchedDate) + "[에까지로부터]*", " ");
        }

        // [2단계] 월 요금 추출 후 문장에서 제거
        Integer price = null;
        String matchedPrice = null;

        // 1) 소수점 만원 단위 (예: 1.7만원)
        m = Pattern.compile("(?:월\\s*)?(\\d+\\.\\d+)\\s*만\\s*원?").matcher(work);
        if (m.find()) {
            price = (int) Math.round(Double.parseDouble(m.group(1)) * 10000);
            matchedPrice = m.group(0);
        }

        // 2) 한글 금액 단위 (예: 1만 7천원, 만5천원, 9천9백원, 2만원)
        if (price == null) {
            String normalized = normalizeKoreanUnits(work);
            m = Pattern.compile("(?:월\\s*)?(?:(\\d*)\\s*만\\s*(?:(\\d+)\\s*천\\s*)?(?:(\\d+)\\s*백\\s*)?(\\d*)\\s*원?|(?:(\\d+)\\s*천\\s*(?:(\\d+)\\s*백\\s*)?(\\d*)\\s*원?))")
                    .matcher(normalized);
            while (m.find()) {
                String token = m.group(0).trim();
                if (token.equals("만") || token.equals("천")) continue;
                int total = 0;
                if (token.contains("만")) {
                    int man = isBlank(m.group(1)) ? 1 : Integer.parseInt(m.group(1));
                    total += man * 10000;
                    if (!isBlank(m.group(2))) total += Integer.parseInt(m.group(2)) * 1000;
                    if (!isBlank(m.group(3))) total += Integer.parseInt(m.group(3)) * 100;
                    if (!isBlank(m.group(4))) total += Integer.parseInt(m.group(4));
                } else {
                    if (!isBlank(m.group(5))) total += Integer.parseInt(m.group(5)) * 1000;
                    if (!isBlank(m.group(6))) total += Integer.parseInt(m.group(6)) * 100;
                    if (!isBlank(m.group(7))) total += Integer.parseInt(m.group(7));
                }
                if (total > 0) {
                    price = total;
                    matchedPrice = token;
                    work = normalized;
                    break;
                }
            }
        }

        // 3) 숫자 + 원 (예: 17,000원, 17000원, 월 9900원)
        if (price == null) {
            m = Pattern.compile("(?:월\\s*)?(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원").matcher(work);
            if (m.find()) {
                price = Integer.parseInt(m.group(1).replace(",", ""));
                matchedPrice = m.group(0);
            }
        }

        // 4) '원' 없이 적힌 금액 숫자 (예: "웨이브 7900 12/5")
        if (price == null) {
            m = Pattern.compile("(?<=\\s)(?:월\\s*)?(\\d{1,3}(?:,\\d{3})+|\\d{3,7})(?=\\s|[에이을를]|$)").matcher(work);
            if (m.find()) {
                price = Integer.parseInt(m.group(1).replace(",", ""));
                matchedPrice = m.group(0);
            }
        }

        // 5) 무료 체험
        if (price == null && work.contains("무료")) {
            price = 0;
            matchedPrice = "무료";
        }

        res.price = (price != null) ? price : 0;
        if (matchedPrice != null) {
            work = work.replaceFirst(Pattern.quote(matchedPrice) + "[에이을를으로]*", " ");
        }

        // [3단계] 남은 문장에서 불필요한 단어 제거 후 서비스명 정리
        String cleaned = work
                .replaceAll("(정기\\s*결제|자동\\s*결제|결제일|결제\\s*예정|결제됨|결제|종료일|종료\\s*예정|종료|마감일|마감|무료\\s*체험|구독료|구독|월\\s*요금|요금|가격|금액|등록해줘|등록|추가해줘|추가|매달|매월|다음\\s*달|이번\\s*달)", " ")
                .replaceAll("[,/\\-:|\\[\\]()]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        cleaned = cleaned.replaceAll("^(에|에서|은|는|이|가|을|를|월)\\s+", "")
                .replaceAll("\\s+(에|에서|은|는|이|가|을|를|됨|예정|임|월)$", "")
                .trim();

        res.service = cleaned;
        return res;
    }

    // ==================== 3. 월별 요약 및 해지 링크 ====================

    public static String getMonthlySummary() {
        List<String[]> list = SubscriptionDAO.getAllSubscriptions();
        if (list.isEmpty()) {
            return "현재 등록된 구독 서비스가 없습니다.";
        }

        StringBuilder sb = new StringBuilder("📊 [구독 지출 현황 요약]\n");
        int total = 0;
        for (String[] sub : list) {
            int price = 0;
            try {
                price = Integer.parseInt(sub[2].replace(",", "").trim());
            } catch (Exception ignored) {
            }
            total += price;
            sb.append(String.format("- %s: %,d원 (결제/종료일: %s)\n", sub[0], price, sub[1]));
        }
        sb.append(String.format("--------------------\n💰 월 총 지출액: %,d원", total));
        return sb.toString();
    }

    public static String getCancelUrl(String serviceName) {
        String lower = serviceName.toLowerCase().replaceAll("\\s+", "");
        if (lower.contains("netflix") || lower.contains("넷플릭스")) {
            return "https://www.netflix.com/cancelplan";
        } else if (lower.contains("youtube") || lower.contains("유튜브")) {
            return "https://www.youtube.com/paid_memberships";
        } else if (lower.contains("tving") || lower.contains("티빙")) {
            return "https://www.tving.com/my/pass";
        } else if (lower.contains("wavve") || lower.contains("웨이브")) {
            return "https://www.wavve.com/my/subscription";
        } else if (lower.contains("disney") || lower.contains("디즈니")) {
            return "https://www.disneyplus.com/account";
        } else if (lower.contains("watcha") || lower.contains("왓챠")) {
            return "https://watcha.com/settings";
        } else if (lower.contains("spotify") || lower.contains("스포티파이")) {
            return "https://www.spotify.com/account/overview/";
        } else if (lower.contains("melon") || lower.contains("멜론")) {
            return "https://www.melon.com/commerce/mypage/product/web/producthistory_listCurUserProdView.htm";
        } else if (lower.contains("naver") || lower.contains("네이버")) {
            return "https://nid.naver.com/membership/my";
        } else if (lower.contains("adobe") || lower.contains("어도비")) {
            return "https://account.adobe.com/plans";
        } else if (lower.contains("coupang") || lower.contains("쿠팡") || lower.contains("와우")) {
            return "https://mc.coupang.com/ssr/desktop/wow/manage";
        } else if (lower.contains("chatgpt") || lower.contains("챗지피티")) {
            return "https://chatgpt.com/#settings/Subscription";
        }
        String encoded = URLEncoder.encode(serviceName + " 해지", StandardCharsets.UTF_8);
        return "https://www.google.com/search?q=" + encoded;
    }

    // ==================== 유틸리티 ====================

    private static JsonObject extractValidJsonObject(String text) {
        if (isBlank(text)) return null;
        int end = text.lastIndexOf('}');
        if (end == -1) return null;

        // 뒤에서부터 '{'를 찾아 가장 깔끔한 JSON 객체 파싱 시도
        int idx = end;
        while ((idx = text.lastIndexOf('{', idx - 1)) != -1) {
            String candidate = text.substring(idx, end + 1);
            try {
                JsonElement el = JsonParser.parseString(candidate);
                if (el.isJsonObject()) return el.getAsJsonObject();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String normalizeDate(String rawDate) {
        if (isBlank(rawDate)) return "";
        Matcher m = Pattern.compile("(\\d{4})\\s*[-./년]\\s*(\\d{1,2})\\s*[-./월]\\s*(\\d{1,2})").matcher(rawDate.trim());
        if (m.find()) {
            int y = Integer.parseInt(m.group(1));
            int mo = Integer.parseInt(m.group(2));
            int d = Integer.parseInt(m.group(3));
            return safeDate(y, mo, d).toString();
        }
        return "";
    }

    private static String getStringSafe(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return "";
        return obj.get(key).getAsString().trim();
    }

    private static int getIntSafe(JsonObject obj, String... keys) {
        for (String key : keys) {
            if (!obj.has(key) || obj.get(key).isJsonNull()) continue;
            JsonElement el = obj.get(key);
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
                return (int) Math.round(el.getAsDouble());
            }
            String digits = el.getAsString().replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) return Integer.parseInt(digits);
        }
        return 0;
    }

    private static String normalizeKoreanUnits(String s) {
        return s.replace("일만", "1만").replace("이만", "2만").replace("삼만", "3만")
                .replace("사만", "4만").replace("오만", "5만").replace("육만", "6만")
                .replace("칠만", "7만").replace("팔만", "8만").replace("구만", "9만")
                .replace("일천", "1천").replace("이천", "2천").replace("삼천", "3천")
                .replace("사천", "4천").replace("오천", "5천").replace("육천", "6천")
                .replace("칠천", "7천").replace("팔천", "8천").replace("구천", "9천")
                .replace("일백", "1백").replace("이백", "2백").replace("삼백", "3백")
                .replace("사백", "4백").replace("오백", "5백").replace("육백", "6백")
                .replace("칠백", "7백").replace("팔백", "8백").replace("구백", "9백");
    }

    private static LocalDate safeDate(int year, int month, int day) {
        if (month < 1) month = 1;
        if (month > 12) month = 12;
        int maxDay = YearMonth.of(year, month).lengthOfMonth();
        if (day < 1) day = 1;
        if (day > maxDay) day = maxDay;
        return LocalDate.of(year, month, day);
    }

    private static LocalDate nextOccurrenceOfDay(LocalDate today, int day) {
        YearMonth current = YearMonth.from(today);
        LocalDate candidate = safeDate(current.getYear(), current.getMonthValue(), day);
        if (candidate.isBefore(today)) {
            YearMonth next = current.plusMonths(1);
            return safeDate(next.getYear(), next.getMonthValue(), day);
        }
        return candidate;
    }

    private static LocalDate nextOccurrenceOfMonthDay(LocalDate today, int month, int day) {
        LocalDate candidate = safeDate(today.getYear(), month, day);
        if (candidate.isBefore(today)) {
            return safeDate(today.getYear() + 1, month, day);
        }
        return candidate;
    }

    private static boolean isValidDate(String dateStr) {
        if (isBlank(dateStr)) return false;
        try {
            LocalDate.parse(dateStr.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (!isBlank(v)) return v.trim();
        }
        return "";
    }
}