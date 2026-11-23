package notification;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class Notification {
    public void sendAlert(String serviceName, String price, String endDate, String cancelUrl) {
        String msg = serviceName + " 결제일(" + endDate + ") 임박!\n"
                   + "결제 예정 금액: " + price + "원\n"
                   + "해지 안내: " + (cancelUrl != null ? cancelUrl : "링크 없음");
        System.out.println("==========[알림]==========\n" + msg + "\n==========================");

        // Swing UI 팝업은 반드시 EDT에서 실행해야 스레드 충돌 방지
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null, msg, "구독 결제 알림", JOptionPane.INFORMATION_MESSAGE);
        });
    }
}