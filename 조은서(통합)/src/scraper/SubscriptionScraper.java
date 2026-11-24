package scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
// Jsoup 라이브러리를 활용하여 인터넷 웹 페이지에서 실제 구독 요금 데이터를 긁어오는 클래스
public class SubscriptionScraper {
	// 특정 웹 페이지 url과 데이터를 추출할 html 요소의 위치를 전달받아, 요금을 숫자로 반환하는 메서드
    public static int getPrice(String targetUrl, String cssSelector) {
        try {
        	// 1. 전달받은 대상 url에 접속하여 해당 웹 페이지의 전체 html 문서를 가져옴
            Document doc = Jsoup.connect(targetUrl).get();
            // 2. 전체 html 문서 중에서, 가격 정보가 들어있는 특정 위치의 요소 하나를 찾아냄
            Element priceElement = doc.selectFirst(cssSelector);
            
            // 3. 해당 요소를 성공적으 찾았을 때 (null이 아니면)
            if (priceElement != null) {
            	// html 요소 안에 적힌 글자를 가져옴. (예: "13,500원" 또는 "US$9.99")
            	// 정규표현식 "[^0-9]"를 사용하여, 숫자가 아닌 모든 문자를 빈 문자열로 치환하여 제거
            	// 결과적으로 "13,500원" -> "13500"이라는 숫자 형태의 문자열만 남게 됨
                String priceText = priceElement.text().replaceAll("[^0-9]", "");
                // DB 저장 및 합산 계산을 위해, 문자열("13,500")을 정수형 숫자(13500)로 변환하여 반환
                return Integer.parseInt(priceText);
            }
        } catch (Exception e) {
        	// 인터넷 연결이 끊기거나, 해당 웹사이트에서 크롤링 봇을 차단하는 등의 에러가 발생하면 로그를 출력
            e.printStackTrace();
        }
        //4. 요금 수집에 실패했거나 에러가 발생한 경우 0을 반환
        // 0을 반환하면, MainClass에서 이를 감지하고 안전장치를 적용하여 프로그램이 멈추지 않게 방어함
        return 0;
    }
}