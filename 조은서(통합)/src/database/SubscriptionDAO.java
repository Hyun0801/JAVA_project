package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class SubscriptionDAO {

    private static Connection getConnection() throws Exception {
        // DB 파일이 아직 없거나 삭제된 경우에도 안전하게 폴더·테이블 보장
        SQLiteManager.createDatabaseAndTable();
        return DriverManager.getConnection(SQLiteManager.getDbUrl());
    }

    // 1. 구독 서비스 정보 저장
    public static void insertSubscription(String serviceName, int price, String endDate, String cancelUrl) {
        String sql = "INSERT INTO subscriptions (service_name, price, end_date, cancel_url) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, serviceName);
            pstmt.setInt(2, price);
            pstmt.setString(3, endDate);
            pstmt.setString(4, cancelUrl);
            pstmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 2. 전체 구독 목록 조회
    public static List<String[]> getAllSubscriptions() {
        List<String[]> subscriptionList = new ArrayList<>();
        String sql = "SELECT service_name, end_date, price, cancel_url FROM subscriptions";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                String[] data = {
                    rs.getString("service_name"),
                    rs.getString("end_date"),
                    String.valueOf(rs.getInt("price")),
                    rs.getString("cancel_url")
                };
                subscriptionList.add(data);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return subscriptionList;
    }

    // 3. 특정 구독 서비스 삭제 (서비스명과 종료일 기준)
    public static void deleteSubscription(String serviceName, String endDate) {
        String sql = "DELETE FROM subscriptions WHERE service_name = ? AND end_date = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, serviceName);
            pstmt.setString(2, endDate);
            pstmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 4. 월별 지출 총액 계산
    public static int calculateMonthlyTotal() {
        List<String[]> list = getAllSubscriptions();
        int total = 0;
        for (String[] sub : list) {
            total += Integer.parseInt(sub[2]);
        }
        return total;
    }
}