package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
// 프로그램 구동 시 데이터를 저장할 SQLite db 파일과 테이블 초기 생성용 관리 클래스
public class SQLiteManager {
	// db 파일과 'subscriptions' 테이블의 존재 확인 후 없을 때 새로 생성
	// 프로그램 시작 시 가장 먼저 호출. 저장 공간 세팅을 해줌
    public static void createDatabaseAndTable() {
        // 1. DB 연결 주소(URL) 설정
    	// 프로젝트 최상위 폴더에 'subscription.db'라는 이름의 파일 형태로 DB를 생성하고 연결
        String url = "jdbc:sqlite:subscription.db";
        
        // 2. 테이블 생성 SQL 쿼리문 세팅
        // 'CREATE TABLE IF NOT EXISTS'를 사용하여 기존 테이블에 없을 때만 새롭게 생성
        String sql = "CREATE TABLE IF NOT EXISTS subscriptions (" +
                     "id INTEGER PRIMARY KEY AUTOINCREMENT, " +	//고유 식별 번호
                     "service_name TEXT NOT NULL, " +	//구독 서비스명 (빈값 허용 안함)
                     "price INTEGER NOT NULL, " +	//결제 금액 (빈값 허용 X)
                     "end_date TEXT NOT NULL, " +	//결제/무료체험 종료일 (빈갑 허용 X)
                     "cancel_url TEXT)";	// 해지 안내 링크 (디스코드 챗봇 전송용)
        
        // 3. DB 연결 및 SQL문 실행 (try-with-resources 문법 적용)
        // 괄호 안에서 db 연결(Connection)과 쿼리 실행 객체(Statement)를 생성
        // 작업이 끝난 후 자동으로 db 연결을 닫아줌 -> 메모리 누수 방지
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
        	// CREATE TABLE SQL 쿼리를 DB로 실행하여 실제 테이블 구축
            stmt.execute(sql);
            // 테이블 생성 완료 안내 문구
            System.out.println("SQLite DB 및 테이블 생성 완료");
        } catch (Exception e) {
        	// DB 파일 생성 권한이 없거나 SQL 문법에 오타가 발생할 경우, 에러의 원인(로그)을 콘솔에 출력해 줌
            e.printStackTrace();
        }
    }
}