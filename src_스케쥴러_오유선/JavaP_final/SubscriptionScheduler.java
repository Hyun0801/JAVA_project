package JavaP_final;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

//주기적으로 구독 상태를 검사하고 알림을 트리거하는 스케줄러 클래스
public class SubscriptionScheduler {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /*
     스케줄러를 시작
     @param subscriptionLoader DB 등에서 전체 구독 목록을 가져오는 함수 (DB 팀과 연동 지점)
     @param onAlertTrigger 조건에 맞을 때 실행할 알림 콜백 (디스코드 알림 팀과 연동 지점)
     */
    public void startScheduler(
            Supplier<List<SubscriptionDto>> subscriptionLoader,
            Consumer<SubscriptionDto> onAlertTrigger) {
        
        // 프로그램 실행 직후 0초 뒤 첫 검사, 이후 24시간마다 주기적으로 실행
        scheduler.scheduleAtFixedRate(() -> {
            try {
                System.out.println("[스케줄러] 오늘 날짜(" + LocalDate.now() + ") 기준 구독 만료일 검사 중...");
                
                // 1. DB에서 구독 리스트 가져오기
                List<SubscriptionDto> subs = subscriptionLoader.get();
                if (subs == null || subs.isEmpty()) {
                    System.out.println("[스케줄러] 검사할 구독 데이터가 없습니다.");
                    return;
                }

                // 2. 각 구독 항목의 날짜 검사 수행 (DateChecker 활용)
                for (SubscriptionDto sub : subs) {
                    LocalDate expiryDate = sub.getExpiryDate();
                    
                    if (DateChecker.is3DaysBefore(expiryDate)) {
                        System.out.println("[알림 감지] '" + sub.getServiceName() + "' 구독 해제 3일 전입니다!");
                        onAlertTrigger.accept(sub); // 알림 시스템 호출
                    } else if (DateChecker.is1DayBefore(expiryDate)) {
                        System.out.println("[알림 감지] '" + sub.getServiceName() + "' 구독 해제 1일 전입니다!");
                        onAlertTrigger.accept(sub); // 알림 시스템 호출
                    }
                }
                
            } catch (Exception e) {
                System.err.println("[스케줄러 오류 발생] " + e.getMessage());
                e.printStackTrace();
            }
        }, 0, 24, TimeUnit.HOURS);
        
        System.out.println("[스케줄러가 성공적으로 구동되었습니다.]");
    }

    // 스케줄러 종료
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