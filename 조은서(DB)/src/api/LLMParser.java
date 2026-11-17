package api;

import database.SubscriptionDAO;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.cdimascio.dotenv.Dotenv;

public class LLMParser {
	
	private static String getApiKey() {
		try {
			Dotenv dotenv = Dotenv.load();
			return dotenv.get("API_KEY");
		} catch (Exception e) {
			System.out.println("보안 키(.env) 파일을 찾을 수 없습니다.");
			return "";
		}
	}
	
	private static final String API_KEY = getApiKey();
	private static final String API_URL = "https://llm.deepaibit.com/v1/chat/completions";
	
	public static void parseAndSave(String userInput) {
		try {
			URL url = new URL(API_URL);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
			conn.setRequestProperty("Content-Type", "application/json; utf-8");
			conn.setDoOutput(true);
			
			String prompt = "다음 문장에서 구독 서비스명, 요금, 결제일을 추출해줘. " 
						  + "서비스명은 반드시 [Netflix, YouTube Premium, Tving, Disney+, Adobe CC] 중 하나로 통일해."
						  + "결과는 다른 말 없이 오직 {\\\"service\\\": \\\"이름\\\", \\\"price\\\": 13000, \\\"date\\\": \\\"2026-11-30\\\"} 형태의 JSON으로만 출력해. "
						  + "분석할 문장: " + userInput;
			
			String jsonInputString = "{"
					+ "\"model\": \"qwen-27b\","
					+ "\"messages\": ["
					+ "  {\"role\": \"system\", \"content\": \"너는 데이터 추출 전문가야. JSON만 응답해.\"},"
					+ "  {\"role\": \"user\", \"content\": \"" + prompt + "\"}"
					+ "],"
					+ "\"temperature\": 0"
					+ "}";
			
			try (OutputStream os = conn.getOutputStream()) {
				byte[] input = jsonInputString.getBytes("utf-8");
				os.write(input, 0, input.length);
			}
		
			int responseCode = conn.getResponseCode(); // 200, 400 등 상태 코드 확인
            BufferedReader br;
            
            if (responseCode >= 200 && responseCode < 300) {
                // 정상 응답일 경우
                br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
            } else {
                // 400 등 에러가 발생했을 경우 에러 스트림을 읽어옴
                br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"));
            }
        
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        
            System.out.println("서버 응답 코드: " + responseCode);
            System.out.println("서버 상세 응답: " + response.toString());
        
            if (responseCode >= 200 && responseCode < 300) {
                try {
                    // Gson을 사용하여 서버 응답(문자열)을 JSON 객체로 변환
                    JsonObject jsonObject = JsonParser.parseString(response.toString()).getAsJsonObject();
                    
                    String extractedService = jsonObject.get("service").getAsString();
                    int extractedPrice = jsonObject.get("price").getAsInt();
                    String extractedDate = jsonObject.get("date").getAsString();
                    
                    System.out.println("\n파싱 성공! DB 저장을 시도합니다.");
                    System.out.println("- 서비스명: " + extractedService);
                    System.out.println("- 결제요금: " + extractedPrice + "원");
                    System.out.println("- 결제일자: " + extractedDate);
                    
                    // 임시 해지 링크 생성
                    String searchUrl = "https://www.google.com/search?q=" + extractedService + "+해지";
                    
                    // 추출한 데이터를 DB에 밀어넣기
                    database.SubscriptionDAO.insertSubscription(extractedService, extractedPrice, extractedDate, searchUrl);
                    System.out.println("DB 저장 완료!");
                    
                } catch (Exception parseEx) {
                    System.out.println("JSON 데이터를 분해하는 중 오류가 발생했습니다.");
                    parseEx.printStackTrace();
                }
            } else {
                System.out.println("통신 에러로 인해 DB 저장을 생략합니다. (API 키 대기 중)");
            }
            // ==============================================================

		} catch (Exception e) {
			System.out.println("API 통신 중 오류 발생");
			e.printStackTrace();
		}
	}
}
