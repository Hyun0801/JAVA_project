package JavaP_final;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class DateChecker {
    public static long getRemainingDays(LocalDate date) {
        return ChronoUnit.DAYS.between(LocalDate.now(), date);
    }
}
