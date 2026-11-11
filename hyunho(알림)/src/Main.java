package notification;

public class NotificationThread extends Thread {
    
    private boolean isRunning = true;

    @Override
    public void run() {
        while (isRunning) {
            try {
                // 추가된 부분: 5초마다 도는지 확인하는 로그
                System.out.println("[시스템] 백그라운드 날짜 체크 중...");
                
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
