package notification;

import javax.swing.JOptionPane;

public class Notification {
    public void sendAlert(String serviceName, String price, String endDate) {
        String msg = serviceName + " 결제일(" + endDate + ") 임박!\n결제 예정 금액: " + price + "원";
        System.out.println("==========[알림]==========\n" + msg + "\n==========================");
        JOptionPane.showMessageDialog(null, msg, "구독 결제 알림", JOptionPane.INFORMATION_MESSAGE);
    }
}
