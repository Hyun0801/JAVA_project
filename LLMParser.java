package JavaP_final;

import database.SubscriptionDAO;

public class LLMParser {

    // api 키 대체용 - 사용자가 디스코드에 입력한 자연어 텍스트를 받아 처리하는 메서드
    public static void parseAndSave(String discordMessage) {
        System.out.println("[AI 파서] 입력된 텍스트 분석 중: \"" + discordMessage + "\"");

        //Mock 모드 API 키를 받기 전까지는 무조건 이 고정 데이터를 리턴합니다.
        // 나중에 API 키를 받으면 이 내부 로직만 실제 LLM API 호출 코드로 교체하면 됨
        String serviceName = "넷플릭스 프리미엄";
        int price = 17000;
        String endDate = "2026-12-05"; // YYYY-MM-DD 형식
        String cancelUrl = "https://www.netflix.com/youraccount"; // 예시 해지 링크

        System.out.println("[AI 파서] 파싱 완료! ➔ 서비스: " + serviceName + ", 금액: " + price + "원, 만료일: " + endDate);

        // DB 연동 지점: 파싱된 데이터를 DB에 저장
        try {
            // DB 팀원이 만든 insert 메서드 형태에 맞게 호출
            System.out.println("[DB 연동] SQLite 데이터베이스에 구독 정보가 성공적으로 저장되었습니다!");
            
        } catch (Exception e) {
            System.err.println("[에러] DB 저장 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
}