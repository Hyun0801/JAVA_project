package analysis;

import java.util.List;
// 서비스들의 요금 데이터를 바탕으로 월별 지출 총액을 계산하는 클래스
public class ExpenseCalculator {

    // DB에서 불러온 전체 구독 서비스 목록(subscriptionList)을 입력받아 지출 총액을 계산하는 메서드
	// 다른 클래스에서 객체 생성 없이 ExpenseCalculaator.calculateTotalExpense()로 바로 호출 가능(static 선언)
    public static int calculateTotalExpense(List<String[]> subscriptionList) {
        // 지출 총액을 누적해서 담아둘 변수를 0으로 초기화
    	int total = 0;
    	//향상된 for문을 사용하여 리스트에 담긴 구독 서비스 정로(String 배열)를 하나씩 반복 처리
        for (String[] sub : subscriptionList) {
            // db에서 가져온 배열의 2번 인덱스에는 price 요금 데이터가 문자열 형태로 들어있음
            int price = Integer.parseInt(sub[2]);
            // 변환된 개별 서비스의 요금을 total 변수에 계속 더해줌(누적 합산)
            total += price;
        }
        // 모든 구독 서비스의 요금이 합산된 최종 지출 총액을 반환
        return total;
    }
}