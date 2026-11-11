package GUI;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;  //데이터 행, 열, 값을 관리하는 클래스
import javax.swing.table.DefaultTableModel; // 표안의 셀이 화면에 어떻게 보일지 설정하는 클래스
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.time.YearMonth;  // 특정 연도와 월 정보만을 다룸
import java.time.format.DateTimeParseException; // 날짜와 사간이 형식이 맞지 않으면 발생하는 예외 
import java.time.temporal.ChronoUnit;

public class MainFrame extends JFrame {
	JTextField nameField = new JTextField(10);     // 서비스명
	JTextField dateField = new JTextField(10);     // 종료/결제일
	JTextField amountField = new JTextField(7);    // 월 요금
	DefaultTableModel listModel;                   // 표의 데이터
	JTable listTable; 
	
	DefaultTableModel spendModel;
	JTable spendTable;
	int SPEND_MONTHS = 6;
	
	public MainFrame() {
		Container ct = getContentPane();
		JPanel mp = new JPanel();
		JPanel sp = new JPanel();
		JPanel np = new JPanel();
		
		setTitle("구독 서비스 결제 및 무료 체험 마감 알림");
		setSize(900, 620);
		
		amountField.addKeyListener(new KeyAdapter() {
			@Override
			public void keyTyped(KeyEvent e) {
				char c = e.getKeyChar();
				if(!Character.isDigit(c)) {
					e.consume();
				}
			}
		});
		
		JTabbedPane tabs = new JTabbedPane(); 
		tabs.addTab("구독 관리", ManagerPanel());
		tabs.addTab("월별 지출", SpendPanel());
		tabs.addTab("알림", NotificationPanel());
		
		ct.add(tabs);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setVisible(true);
	}
	
	private JPanel ManagerPanel() {
		JPanel mp = new JPanel(new BorderLayout(8, 8));
		
		JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
		form.setBorder(BorderFactory.createTitledBorder("구독 정보 입력"));
		form.add(new JLabel("서비스명"));
		form.add(nameField);
		form.add(new JLabel("종료/결제일(yyyy-MM-dd)"));
		form.add(dateField);
		form.add(new JLabel("월 요금(원)"));
		form.add(amountField);
        
		// 구독 목록 표
		String[] columns = {"서비스명", "종료/결제일", "남은 일수", "월 요금(원)"};
		listModel = new DefaultTableModel(columns, 0);
		listTable = new JTable(listModel);
		listTable.setRowHeight(26);
		
		JButton sB = new JButton("저장");
        JButton dB = new JButton("삭제");
        JButton cB = new JButton("새로 입력");
        JButton rB = new JButton("새로고침");
		// 버튼을 눌렀을 때 할 일 연결
        sB.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				saveSubscription();
			}
		});
 
		dB.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				deleteSubscription();
			}
		});
 
		cB.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				clearForm();
			}
		});
 
		rB.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				refreshDays();
			}
		});
        
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(rB);
        buttons.add(cB);
        buttons.add(dB);
        buttons.add(sB);
        
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(form, BorderLayout.NORTH);     
        topPanel.add(buttons, BorderLayout.SOUTH);
        
        mp.add(topPanel, BorderLayout.NORTH);
        mp.add(new JScrollPane(listTable), BorderLayout.CENTER);
        return mp;
	}
	
	private JPanel SpendPanel() {
		JPanel sp = new JPanel(new BorderLayout(8, 8));
 
		// 위쪽 안내 문구
		sp.add(new JLabel("※ 등록된 모든 구독이 매달 월 요금만큼 결제되는 것으로 계산합니다."),
				BorderLayout.NORTH);
 
		// 표 만들기 (열 제목과 내용은 refreshSpending()이 채움)
		spendModel = new DefaultTableModel();
		spendTable = new JTable(spendModel);
 
		sp.add(new JScrollPane(spendTable), BorderLayout.CENTER);
 
		refreshSpending();   // 처음 한 번 채우기
		return sp;
	}
	
	private JPanel NotificationPanel() {
		JPanel np = new JPanel();
		
		return np;
	}
	
	// 구독 목록 표를 읽어서 월별 지출 표를 다시 만든다
	private void refreshSpending() {
		YearMonth start = YearMonth.now();   // 이번 달

		// 1. 열 제목: 서비스 | 2026-11 | 2026-12 | ...
		String[] cols = new String[SPEND_MONTHS + 1];
		cols[0] = "서비스";
		for (int i = 0; i < SPEND_MONTHS; i++) {
			cols[i + 1] = start.plusMonths(i).toString();   
		}
		spendModel.setColumnIdentifiers(cols);
		spendModel.setRowCount(0);   // 기존 내용 비우기

		// 2. 서비스마다 한 줄씩 추가
		for (int r = 0; r < listModel.getRowCount(); r++) {
			String name = (String) listModel.getValueAt(r, 0);     // 1번째 칸 = 서비스명
			int amount = (Integer) listModel.getValueAt(r, 3);     // 4번째 칸 = 월 요금

			Object[] row = new Object[SPEND_MONTHS + 1];
			row[0] = name;
			for (int i = 0; i < SPEND_MONTHS; i++) {
				row[i + 1] = String.format("%,d원", amount);
			}
			spendModel.addRow(row);
		}
	}
	
	private void saveSubscription() {
		// 1. 입력값 읽기
		String name = nameField.getText().trim();
		String dateText = dateField.getText().trim();
		String amountText = amountField.getText().trim();
 
		// 2. 서비스명이 비었는지 검사
		if (name.isEmpty()) {
			JOptionPane.showMessageDialog(this, "서비스명을 입력하세요.");
			return;
		}
 
		// 3. 날짜 검사 (형식이 틀리면 안내 후 종료)
		LocalDate date;
		try {
			date = LocalDate.parse(dateText);
		} catch (DateTimeParseException e) {
			JOptionPane.showMessageDialog(this, "날짜를 2026-11-30 형식으로 입력하세요.");
			return;
		}
 
		// 4. 월 요금 검사 
		int amount;
		try {
			amount = Integer.parseInt(amountText.replace(",", ""));
		} catch (NumberFormatException e) {
			JOptionPane.showMessageDialog(this, "월 요금은 숫자로 입력하세요.");
			return;
		}
 
		// 5. 오늘부터 종료일까지 남은 일수 계산
		int daysLeft = (int) ChronoUnit.DAYS.between(LocalDate.now(), date);
 
		// 6. 표에 한 줄 추가
		listModel.addRow(new Object[]{name, date.toString(), daysLeft, amount});
 
		// TODO: DB 담당자 연결 지점 → 여기서 DB에 저장하는 메서드 호출
 
		// 7. 입력칸 비우기
		clearForm();
		refreshSpending();
	}
 
	// [삭제] 표에서 선택한 줄 삭제
	private void deleteSubscription() {
		int row = listTable.getSelectedRow();   // 선택한 줄 번호
		if (row == -1) {
			JOptionPane.showMessageDialog(this, "삭제할 줄을 먼저 선택하세요.");
			return;
		}
 
		int answer = JOptionPane.showConfirmDialog(this, "선택한 구독을 삭제할까요?",
				"삭제 확인", JOptionPane.YES_NO_OPTION);
		if (answer != JOptionPane.YES_OPTION) {
			return;
		}
 
		listModel.removeRow(row);
 
		
	}
 
	// [새로 입력] 입력칸을 모두 비우고 표 선택 해제
	private void clearForm() {
		nameField.setText("");
		dateField.setText("");
		amountField.setText("");
		listTable.clearSelection();
	}
 
	// [새로고침] 표에 있는 모든 줄의 남은 일수를 오늘 기준으로 다시 계산
	private void refreshDays() {
		for (int i = 0; i < listModel.getRowCount(); i++) {
			String dateText = (String) listModel.getValueAt(i, 1);   // 2번째 칸 = 종료/결제일
			LocalDate date = LocalDate.parse(dateText);
			int daysLeft = (int) ChronoUnit.DAYS.between(LocalDate.now(), date);
			listModel.setValueAt(daysLeft, i, 2);                    // 3번째 칸 = 남은 일수
		}
 
		refreshSpending();
	}
}

