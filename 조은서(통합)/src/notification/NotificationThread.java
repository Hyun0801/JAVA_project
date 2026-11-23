package notification;

import database.SubscriptionDAO;
import gui.MainFrame;
import scheduler.DateChecker;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NotificationThread extends Thread {
    private volatile boolean isRunning = true;
    private MainFrame mainFrame;
    private Notification notification = new Notification();
    private Set<String> alerted = new HashSet<>();

    public NotificationThread(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setDaemon(true); // 메인 창 종료 시 스레드도 함께 종료
    }

    @Override
    public void run() {
        while (isRunning) {
            try {
                check();
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                isRunning = false;
                Thread.currentThread().interrupt();
            }
        }
    }

    private void check() {
        try {
            List<String[]> subs = SubscriptionDAO.getAllSubscriptions();
            for (String[] sub : subs) {
                String name = sub[0];
                String dateStr = sub[1];
                String price = sub[2];
                String cancelUrl = sub[3];

                if (dateStr == null || dateStr.trim().isEmpty()) continue;

                long days = DateChecker.getRemainingDays(LocalDate.parse(dateStr.trim()));
                // 0~4일 전(계획서 3~4일 전 및 D-Day 임박 포함) 알림 발송
                if (days >= 0 && days <= 4) {
                    String key = name + "_" + dateStr + "_" + days;
                    if (!alerted.contains(key)) {
                        notification.sendAlert(name, price, dateStr, cancelUrl);
                        alerted.add(key);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopThread() {
        this.isRunning = false;
        this.interrupt();
    }
}