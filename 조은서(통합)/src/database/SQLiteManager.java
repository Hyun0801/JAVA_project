package database;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

// 프로그램 구동 시 데이터를 저장할 SQLite db 파일과 테이블 초기 생성용 관리 클래스
public class SQLiteManager {

    private static final Path DB_DIR = Paths.get(System.getProperty("user.home"), ".subalert");
    private static final Path DB_PATH = DB_DIR.resolve("subscription.db");

    public static String getDbUrl() {
        return "jdbc:sqlite:" + DB_PATH.toAbsolutePath();
    }

    // db 파일과 'subscriptions' 테이블의 존재 확인 후 없을 때 새로 생성
    // 프로그램 시작 시 가장 먼저 호출. 저장 공간 세팅을 해줌
    public static void createDatabaseAndTable() {
        try {
            // 1. 사용자 홈 폴더 아래 전용 저장 폴더(.subalert) 생성
            if (!Files.exists(DB_DIR)) {
                Files.createDirectories(DB_DIR);
            }

            // 2. DB 연결 주소(URL) 설정 (예: C:\Users\사용자명\.subalert\subscription.db)
            String url = getDbUrl();

            // 3. 테이블 생성 SQL 쿼리문 세팅
            String sql = "CREATE TABLE IF NOT EXISTS subscriptions (" +
                         "id INTEGER PRIMARY KEY AUTOINCREMENT, " +   // 고유 식별 번호
                         "service_name TEXT NOT NULL, " +             // 구독 서비스명 (빈값 허용 안함)
                         "price INTEGER NOT NULL, " +                // 결제 금액 (빈값 허용 X)
                         "end_date TEXT NOT NULL, " +               // 결제/무료체험 종료일 (빈값 허용 X)
                         "cancel_url TEXT)";                          // 해지 안내 링크

            // 4. DB 연결 및 SQL문 실행
            try (Connection conn = DriverManager.getConnection(url);
                 Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
                System.out.println("SQLite DB 및 테이블 생성 완료: " + DB_PATH.toAbsolutePath());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}