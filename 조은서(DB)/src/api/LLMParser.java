package api;

import database.SubscriptionDAO;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import java.io.FileInputStream;
import java.util.Properties;

public class LLMParser {
	

	private static String getApiKey() {
		Properties prop = new Properties();
		try (FileInputStream fis = new FileInputStream("config.properties")) {
			prop.load(fis);
			return prop.getProperty("API_KEY");
		} catch (Exception e) {
			System.out.println("보안 키(config.properties) 파일을 찾을 수 없습니다.");
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
        
            // 여기에 JSON 결과를 분해해서 SubscriptionDAO.insertSubscription 에 넣는 코드 추가 예정
		} catch (Exception e) {
			System.out.println("API 통신 중 오류 발생");
			e.printStackTrace();
		}
	}
}
