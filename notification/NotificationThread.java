package notification;

import GUI.MainFrame;
import JavaP_final.DateChecker;
import java.time.LocalDate;
import java.util.ArrayList;

public class NotificationThread extends Thread {
    private boolean isRunning = true;
    private MainFrame mainFrame;
    private Notification notification = new Notification();
    private ArrayList<String> alerted = new ArrayList<>();

    public NotificationThread(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
    }

    @Override
    public void run() {
        while (isRunning) {
            try {
                check();
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                isRunning = false;
            }
        }
    }

    private void check() {
        if (mainFrame == null || mainFrame.listModel == null) return;
        if (mainFrame.notiModel != null) mainFrame.notiModel.setRowCount(0);

        for (int i = 0; i < mainFrame.listModel.getRowCount(); i++) {
            String name = mainFrame.listModel.getValueAt(i, 0).toString();
            String date = mainFrame.listModel.getValueAt(i, 1).toString();
            String price = mainFrame.listModel.getValueAt(i, 3).toString();

            long days = DateChecker.getRemainingDays(LocalDate.parse(date));
            if (days >= 0 && days <= 3) {
                if (mainFrame.notiModel != null) {
                    mainFrame.notiModel.addRow(new Object[]{name, date, "D-" + days, price + "원"});
                }
                String key = name + date + days;
                if (!alerted.contains(key)) {
                    notification.sendAlert(name, price, date);
                    alerted.add(key);
                }
            }
        }
    }

    public void stopThread() {
        this.isRunning = false;
    }
}
