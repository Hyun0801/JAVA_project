package notification;

public class Notification {
    
    public void sendAlert(String serviceName, String price, String endDate) {
        System.out.println("==========[알림]==========");
        System.out.println(serviceName +"결제일("+endDate+")!임박!");
        System.out.println("결제 예정 금액:"+price+"원");
        System.out.println("==========================");
    }
}
