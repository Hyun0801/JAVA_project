package main;

import database.SQLiteManager;
import discord.DiscordBotListener;
import gui.MainFrame;
import notification.NotificationThread;

public class MainClass {
    public static void main(String[] args) {
        // 1. SQLite DB 및 테이블 생성 (최초 실행 시 파일 생성)
        SQLiteManager.createDatabaseAndTable();

        // 2. 디스코드 챗봇 백그라운드 실행(배포용으로 빌드하기 전 주석 처리)
        //DiscordBotListener.startBot();

        // 3. GUI 화면 실행
        javax.swing.SwingUtilities.invokeLater(() -> {
            MainFrame mainFrame = new MainFrame();

            // 4. 백그라운드 알림 스레드 구동 (5초마다 D-Day 체크 및 팝업 알림)
            NotificationThread notiThread = new NotificationThread(mainFrame);
            notiThread.start();
        });
    }
}