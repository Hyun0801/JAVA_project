package analysis;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
//구독 서비스의 만료일(결제일)과 관련된 날짜 분석을 담당하는 클래스
public class DateAnalyzer {

	// 문자열 형태의 종료일(endDateStr)을 입력받아, 오늘을 기준으로 만료일이 3~4일 남았는지 판별하는 메서드
    public static boolean isExpiringSoon(String endDateStr) {
        try {
        	// 1. 시스템의 현재 날짜를 가져옴
            LocalDate today = LocalDate.now();
            // 2. 문자열로 된 날짜 데이터를 자바가 계산할 수 있는 날짜 객체로(LocalDate) 변환
            LocalDate endDate = LocalDate.parse(endDateStr, DateTimeFormatter.ISO_DATE);
            // 3. today와 endDate 사이의 남은 일 수 계산
            long daysBetween = ChronoUnit.DAYS.between(today, endDate);

            // 종료일이 3일 또는 4일 남았을 경우 true 반환
            if (daysBetween == 3 || daysBetween == 4) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 남은 기간이 3~4일이 아니거나 에러가 발생한 경우 무조건 false를 반환. 알림을 보내지 않음
        return false;
    }
}