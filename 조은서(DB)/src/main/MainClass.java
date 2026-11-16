package main;

import scraper.SubscriptionScraper;
import database.SubscriptionDAO;
import database.SQLiteManager;
import analysis.DateAnalyzer;
import scheduler.SubscriptionScheduler; 
import scheduler.SubscriptionDto; 

import java.time.LocalDate;   
import java.util.ArrayList;   
import java.util.List;

public class MainClass {
    public static void main(String[] args) {
        
        SQLiteManager.createDatabaseAndTable();

        //[테스트용] 기존 데이터 초기화
        // 매번 실행할 때마다 기존 데이터를 싹 비워주어 중복 누적을 방지
        // 추후 gui 및 챗봇 적용 후 삭제할 코드
        SubscriptionDAO.clearAllSubscriptions();

        // 1.[테스트용] 기존 데이터 초기화
        // Jsoup을 통해 넷플릭스 요금 크롤링을 시도하고, 실패 시 안전장치로 13500원을 기본값으로 적용
        int netflixPrice = SubscriptionScraper.getPrice("https://www.netflix.com/kr/", ".price-amount");
        if (netflixPrice == 0) {
            netflixPrice = 13500; 
        }
        
        // 나머지 4개 서비스의 고정 요금을 임의로 설정해 둠
        int youtubePrice = 14900; 
        int tvingPrice = 13500;   
        int disneyPrice = 9900;   
        int adobePrice = 62000;   

        // 2.[테스트용] 5개 주요 구독 서비스 정보 및 [공식 해지/관리 링크]를 SQLite db에 강제 일괄 등록
        // 추후 삭제
        SubscriptionDAO.insertSubscription("Netflix", netflixPrice, "2026-11-30", "https://www.netflix.com/youraccount");
        SubscriptionDAO.insertSubscription("YouTube Premium", youtubePrice, "2026-11-09", "https://www.youtube.com/paid_memberships");
        SubscriptionDAO.insertSubscription("Tving", tvingPrice, "2026-12-15", "https://www.tving.com/my/pass");
        SubscriptionDAO.insertSubscription("Disney+", disneyPrice, "2026-12-20", "https://www.disneyplus.com/account");
        SubscriptionDAO.insertSubscription("Adobe CC", adobePrice, "2026-12-01", "https://account.adobe.com/plans");

        System.out.println("5대 주요 구독 서비스 정보가 데이터베이스에 성공적으로 적재되었습니다.\n");

        // 3. DB에 저장된 전체 구독 목록을 불러옴
        // 이후 Swing(JTable) 화면에 데이터를 띄워주기 위해 사용될 핵심 코드
        List<String[]> allSubscriptions = SubscriptionDAO.getAllSubscriptions();
        
        System.out.println("========== [현재 등록된 구독 서비스 목록] ==========");
        for (String[] sub : allSubscriptions) {
            String serviceName = sub[0];	 //서비스명
            String endDate = sub[1];	//종료일
            String price = sub[2];	//요금
            String cancelUrl = sub[3]; // 취소 링크 가져오기

            boolean isExpiring = DateAnalyzer.isExpiringSoon(endDate);
            // 만료 임박(true)일 경우 경고. (챗봇 알림 전송 조거문 활용)
            String alertBadge = isExpiring ? " [⚠️ 만료 임박! 알림 대상]" : "";

            System.out.println("- " + serviceName + " | " + price + "원 | 종료일: " + endDate + alertBadge);
            System.out.println("  └ 해지 링크: " + cancelUrl);
        }
        System.out.println("=================================================");

        // 5. 월별 구독 지출 총액 분석 결과 출력
        // ExpenseCalculator(또는 DAO)를 통해 계산된 총액은 추후 Swing 대시보드 하단이나 챗봇 요약 메시지로 전달될 예정
        int totalExpenditure = SubscriptionDAO.calculateMonthlyTotal();
        System.out.println("이번 달 구독 지출 총액: " + totalExpenditure + "원");

        // 7. [테스트용] LLM 자연어 파싱 챗봇 입력 시뮬레이션
        System.out.println("\n========== [LLM 자연어 파싱 테스트] ==========");
        String testUserInput = "넷플릭스 프리미엄 17000원 담달 5일에 나감";
        System.out.println("가상 사용자 입력: " + testUserInput);
        LLMParser.parseAndSave(testUserInput);
        System.out.println("=============================================\n");
        
        // 6. 스케줄러
        SubscriptionScheduler scheduler = new SubscriptionScheduler();
        // 스케줄러의 startScheduler 메서드 실행
        scheduler.startScheduler(
            // 1. 첫 번째 파라미터 (DB 데이터를 가져와 DTO로 변환하여 스케줄러에 전달)
            () -> {
                List<String[]> rawData = SubscriptionDAO.getAllSubscriptions();
                List<SubscriptionDto> dtoList = new ArrayList<>();
                
                for (String[] sub : rawData) {
                    String serviceName = sub[0];
                    // DB에 문자열로 저장된 "2026-11-30"을 LocalDate 객체로 변환
                    LocalDate expiryDate = LocalDate.parse(sub[1]); 
                    dtoList.add(new SubscriptionDto(serviceName, expiryDate));
                }
                return dtoList;
            },
            
            // 2. 두 번째 파라미터 (스케줄러가 알림 대상을 찾았을 때 실행할 행동)
            (dto) -> {
                System.out.println("\n[챗봇 전송 대기] 디스코드 봇을 통해 '" + dto.getServiceName() + "' 만료 알림을 전송해야 합니다!");
                // (나중에 디스코드 봇 챗봇 클래스가 통합되면, 이 자리에 봇 전송 코드를 넣습니다.)
            }
        );
    }
}
