package JavaP_final;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// 오늘 날짜와 만료일 비교해 남은 일수 계산하고 정확히 1일, 3일 전 판별하는 로직
public class DateChecker {
    
    // 오늘 날짜와 만료일을 비교하여 남은 일수 계산 (만료일 - 오늘)
    public static long getRemainingDays(LocalDate expiryDate) {
        if (expiryDate == null) {
            return -1;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }
    
    // 구독 해제 3일 전인지 확인
    public static boolean is3DaysBefore(LocalDate expiryDate) {
        return getRemainingDays(expiryDate) == 3;
    }
    
    // 구독 해제 1일 전인지 확인
    public static boolean is1DayBefore(LocalDate expiryDate) {
        return getRemainingDays(expiryDate) == 1;
    }
}