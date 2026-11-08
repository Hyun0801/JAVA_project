package notification;

public class NotificationThread extends Thread {
    
    private boolean isRunning = true;

    @Override
    public void run() {
        while (isRunning) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                isRunning = false;
            }
        }
    }

    public void stopThread() {
        this.isRunning = false;
    }
}
