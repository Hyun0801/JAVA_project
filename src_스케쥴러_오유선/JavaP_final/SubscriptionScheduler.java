package Scheduler;

import database.SubscriptionDAO;
import analysis.DateAnalyzer;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

// 주기적으로 DB의 구독 상태를 검사하고 만료 임박 시 알림을 트리거하는 스케줄러 클래스

public class SubscriptionScheduler {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // 스케쥴러 시작
    // @param onAlertTrigger 조건(만료 임박)에 맞을 때 실행할 알림 콜백 (디스코드 알림 팀과 연동 지점)
    public void startScheduler(Consumer<String[]> onAlertTrigger) {
        
        // 프로그램 실행 직후 0초 뒤 첫 검사, 이후 24시간마다 주기적으로 실행
        scheduler.scheduleAtFixedRate(() -> {
            try {
                System.out.println("[스케줄러] 데이터베이스 구독 상태 검사 시작...");
                
                // 1. DB 팀원이 만든 DAO를 통해 전체 구독 목록(List<String[]>) 가져오기
                List<String[]> allSubscriptions = SubscriptionDAO.getAllSubscriptions();
                
                if (allSubscriptions == null || allSubscriptions.isEmpty()) {
                    System.out.println("[스케줄러] 검사할 구독 데이터가 없습니다.");
                    return;
                }

                // 2. 각 구독 항목의 날짜 검사 수행
                for (String[] sub : allSubscriptions) {
                    String serviceName = sub[0];
                    String endDate = sub[1];
                    
                    // 분석 파트의 날짜 검사 로직 활용
                    boolean isExpiring = DateAnalyzer.isExpiringSoon(endDate);
                    
                    if (isExpiring) {
                        System.out.println("[알림 감지] '" + serviceName + "' 구독 만료가 임박했습니다!");
                        
                        // 3. 조건에 맞을 때 디스코드 알림 팀의 기능 호출 (콜백 전달)
                        onAlertTrigger.accept(sub); 
                    }
                }
                
            } catch (Exception e) {
                System.err.println("[스케줄러 오류 발생] " + e.getMessage());
                e.printStackTrace();
            }
        }, 0, 24, TimeUnit.HOURS);
        
        System.out.println("[스케줄러가 성공적으로 구동되었습니다.]");
    }

    //스케쥴러 종료
    public void stopScheduler() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("[스케줄러가 종료되었습니다.]");
    }
}