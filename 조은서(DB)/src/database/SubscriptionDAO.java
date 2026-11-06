package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
//SQLite DB와 직접 통신하여 데이터의 저장, 조회, 삭제를 처리하는 DAO 클래스
public class SubscriptionDAO {
	//DB 연결을 위한 고정 주소(URL).
    private static final String url = "jdbc:sqlite:subscription.db";
    // 1. 새로운 구독 서비스 정보를 DB에 저장하는 메서드
    public static void insertSubscription(String serviceName, int price, String endDate, String cancelUrl) {
    	// SQL 인젝션 해킹을 방지하고 안전하게 데이터를 넣기 위해 값 부분을 ?로 비워둠
        String sql = "INSERT INTO subscriptions (service_name, price, end_date, cancel_url) VALUES (?, ?, ?, ?)";
        // try-with-resources 구문을 사용하여 작업이 끝난 후 DB 연결을 안전하게 닫아줌
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // 위 sql문의 ? 순서에 맞게 실제 받아온 변수 값들을 매핑
            pstmt.setString(1, serviceName);
            pstmt.setInt(2, price);
            pstmt.setString(3, endDate);
            pstmt.setString(4, cancelUrl);
            // 완성된 sql 쿼리를 싱행. db에 최종 기록
            pstmt.executeUpdate();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // 2. db에 저장된 모든 구독 서비스 목록을 불러오는 메서드
    public static List<String[]> getAllSubscriptions(){
    	// 조회된 데이터들을 담아둘 빈 리스트 생성
        List<String[]> subscriptionList = new ArrayList<>();
        // subscriptions 테이블에서 서비스명, 종료일, 요금, 해지 링크 컬럼을 가져옴
        String sql = "SELECT service_name, end_date, price, cancel_url FROM subscriptions";
        
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql);
            // 뭐리를 싱행하고 그 결과를 ResultSet: 객체에 담음 
        	ResultSet rs = pstmt.executeQuery()) {
            // rs.next()를 통해 검색된 데이터의 다음 줄이 존재하는 동안 계속 반복
            while (rs.next()) {
            	//한 줄의 데이터를 읽어와서 String 배열에 순서대로 담음
                String[] data = {
                    rs.getString("service_name"),     // index 0: 서비스 이름
                    rs.getString("end_date"),         // index 1: 종료일
                    String.valueOf(rs.getInt("price")), // index 2: 요금 (숫자를 문자열로 변환 후 저장)
                    rs.getString("cancel_url")        // index 3: 해지 링크
                };
                // 완성된 배열 1개(서비스 1개 정보)를 리스트에 추가
                subscriptionList.add(data);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 모든 서비스 정보가 담긴 리스트 반환. (이후 Swing 화면이나 ExpenseCalculator에서 사용)
        return subscriptionList;
    }

    // 3. 월별 구독 지출 총액을 dao 내에서 직접 계산하여 반환하는 메서드
    public static int calculateMonthlyTotal() {
    	// getAllSubscriptions()를 재사용하여 전체 데이터 가져옴
        List<String[]> list = getAllSubscriptions();
        int total = 0;
        // 향상된 for문으로 각 서비스의 요금(index 2)을 정수로 변환한 뒤 모두 더함
        for (String[] sub : list) {
            total += Integer.parseInt(sub[2]);
        }
        return total;
    }
    
    //[테스트용] 테이블의 모든 구독 데이터 지우는 메서드
    // gui 및 챗봇 적용 전까지, MainClass를 생성할 때마다 중복으로
    // 누적되는 것을 방지하기 위해 프로그램 시작 시 테이블을 초기화 하는 용도
    public static void clearAllSubscriptions() {
        String sql = "DELETE FROM subscriptions";
        
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // 조건 없이 DELETE 쿼리 실행. 테이블 내부의 모든 행 삭제
        	pstmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}