package JavaP_final;

import java.time.LocalDate;

// 데이터를 주고받는 DTO 구조
public class SubscriptionDto {
    private String serviceName;
    private LocalDate expiryDate;

    public SubscriptionDto(String serviceName, LocalDate expiryDate) {
        this.serviceName = serviceName;
        this.expiryDate = expiryDate;
    }

    public String getServiceName() {
        return serviceName;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }
}