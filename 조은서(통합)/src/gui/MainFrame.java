package gui;

import api.LLMParser;
import database.SubscriptionDAO;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class MainFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    JTextField aiInputField = new JTextField(38);
    JButton aiRegisterBtn = new JButton("AI 자동 등록");

    JTextField nameField = new JTextField(10);
    JTextField dateField = new JTextField(10);
    JTextField amountField = new JTextField(7);

    String[] columns = {"서비스명", "종료/결제일", "남은 일수", "월 요금(원)"};
    public DefaultTableModel listModel = new DefaultTableModel(columns, 0) {
        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }
    };
    public JTable listTable = new JTable(listModel);

    public DefaultTableModel spendModel;
    public JTable spendTable;
    int SPEND_MONTHS = 6;

    public DefaultTableModel notiModel;
    public JTable notiTable;
    int END_DAYS = 31;

    public MainFrame() {
        setTitle("구독 서비스 결제 및 무료 체험 마감 알림");
        setSize(900, 660);

        amountField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if (!Character.isDigit(c) && c != KeyEvent.VK_BACK_SPACE && c != KeyEvent.VK_DELETE) {
                    e.consume();
                }
            }
        });

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("구독 관리", ManagerPanel());
        tabs.addTab("월별 지출", SpendPanel());
        tabs.addTab("알림", NotificationPanel());

        // 디스코드 챗봇이나 AI 등록으로 DB가 바뀌면 Swing 표를 자동 갱신
        LLMParser.setOnDataUpdated(() -> SwingUtilities.invokeLater(this::loadDataFromDB));

        loadDataFromDB();

        add(tabs);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public void loadDataFromDB() {
        listModel.setRowCount(0);
        List<String[]> dbData = SubscriptionDAO.getAllSubscriptions();
        for (String[] sub : dbData) {
            try {
                String name = sub[0];
                String dateText = sub[1].trim();
                int amount = Integer.parseInt(sub[2].trim());

                int daysLeft = (int) ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(dateText));
                listModel.addRow(new Object[]{name, dateText, daysLeft, amount});
            } catch (Exception e) {
                System.err.println("잘못된 DB 행을 건너뜁니다: " + e.getMessage());
            }
        }
        refreshSpending();
        refreshNotifications();
    }

    private JPanel ManagerPanel() {
        JPanel mp = new JPanel(new BorderLayout(8, 8));

        // 1. AI 자연어 자동 등록 영역
        JPanel aiForm = new JPanel(new FlowLayout(FlowLayout.LEFT));
        aiForm.setBorder(BorderFactory.createTitledBorder("AI 자연어 자동 등록 (예: 넷플릭스 프리미엄 17,000원 다음 달 5일 결제)"));
        aiForm.add(aiInputField);
        aiForm.add(aiRegisterBtn);

        aiRegisterBtn.addActionListener(e -> registerWithAI());
        aiInputField.addActionListener(e -> registerWithAI()); // 엔터키로 바로 실행

        // 2. 직접 입력 영역
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        form.setBorder(BorderFactory.createTitledBorder("구독 정보 직접 입력"));
        form.add(new JLabel("서비스명"));
        form.add(nameField);
        form.add(new JLabel("종료/결제일(yyyy-MM-dd)"));
        form.add(dateField);
        form.add(new JLabel("월 요금(원)"));
        form.add(amountField);

        listTable.setRowHeight(26);

        JButton sB = new JButton("저장");
        JButton dB = new JButton("삭제");
        JButton cB = new JButton("새로 입력");
        JButton rB = new JButton("새로고침");

        sB.addActionListener(e -> saveSubscription());
        dB.addActionListener(e -> deleteSubscription());
        cB.addActionListener(e -> clearForm());
        rB.addActionListener(e -> refreshDays());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(rB);
        buttons.add(cB);
        buttons.add(dB);
        buttons.add(sB);

        JPanel topPanel = new JPanel(new BorderLayout(4, 4));
        topPanel.add(aiForm, BorderLayout.NORTH);
        topPanel.add(form, BorderLayout.CENTER);
        topPanel.add(buttons, BorderLayout.SOUTH);

        mp.add(topPanel, BorderLayout.NORTH);
        mp.add(new JScrollPane(listTable), BorderLayout.CENTER);
        return mp;
    }

    // 백그라운드 스레드에서 LLM API 호출 (화면 멈춤 방지)
    private void registerWithAI() {
        String text = aiInputField.getText().trim();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(this, "등록할 구독 문장을 입력하세요.\n예: 넷플릭스 프리미엄 17,000원 다음 달 5일 결제");
            return;
        }

        aiRegisterBtn.setEnabled(false);
        aiRegisterBtn.setText("분석 중...");

        new Thread(() -> {
            String resultMsg = LLMParser.parseAndSave(text);
            SwingUtilities.invokeLater(() -> {
                aiRegisterBtn.setEnabled(true);
                aiRegisterBtn.setText("AI 자동 등록");
                if (resultMsg.startsWith("✅")) {
                    aiInputField.setText("");
                }
                JOptionPane.showMessageDialog(this, resultMsg, "AI 자동 등록 결과", JOptionPane.INFORMATION_MESSAGE);
            });
        }).start();
    }

    private JPanel SpendPanel() {
        JPanel sp = new JPanel(new BorderLayout(8, 8));
        sp.add(new JLabel("※ 등록된 모든 구독이 매달 월 요금만큼 결제되는 것으로 계산합니다."), BorderLayout.NORTH);

        spendModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        spendTable = new JTable(spendModel);
        sp.add(new JScrollPane(spendTable), BorderLayout.CENTER);

        return sp;
    }

    private JPanel NotificationPanel() {
        JPanel np = new JPanel(new BorderLayout(8, 8));

        String[] notiColumns = {"서비스명", "종료/결제일", "남은 일수", "알림"};
        notiModel = new DefaultTableModel(notiColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        notiTable = new JTable(notiModel);

        np.add(new JScrollPane(notiTable), BorderLayout.CENTER);
        return np;
    }

    public void refreshNotifications() {
        if (notiModel == null || listModel == null) return;
        notiModel.setRowCount(0);

        for (int day = 0; day <= END_DAYS; day++) {
            for (int r = 0; r < listModel.getRowCount(); r++) {
                try {
                    String name = listModel.getValueAt(r, 0).toString();
                    String dateText = listModel.getValueAt(r, 1).toString();

                    LocalDate date = LocalDate.parse(dateText);
                    int daysLeft = (int) ChronoUnit.DAYS.between(LocalDate.now(), date);

                    if (daysLeft == day) {
                        String dday = (day == 0) ? "D-Day" : "D-" + day;
                        String message = (day == 0) ? "오늘 종료/결제될 예정입니다." : day + "일 후 종료/결제될 예정입니다.";
                        notiModel.addRow(new Object[]{name, dateText, dday, message});
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void refreshSpending() {
        if (spendModel == null || listModel == null) return;
        YearMonth start = YearMonth.now();

        String[] cols = new String[SPEND_MONTHS + 1];
        cols[0] = "서비스";
        for (int i = 0; i < SPEND_MONTHS; i++) {
            cols[i + 1] = start.plusMonths(i).toString();
        }
        spendModel.setColumnIdentifiers(cols);
        spendModel.setRowCount(0);

        int monthlyTotal = 0;
        for (int r = 0; r < listModel.getRowCount(); r++) {
            String name = listModel.getValueAt(r, 0).toString();
            int amount = Integer.parseInt(listModel.getValueAt(r, 3).toString());
            monthlyTotal += amount;

            Object[] row = new Object[SPEND_MONTHS + 1];
            row[0] = name;
            for (int i = 0; i < SPEND_MONTHS; i++) {
                row[i + 1] = String.format("%,d원", amount);
            }
            spendModel.addRow(row);
        }

        if (listModel.getRowCount() > 0) {
            Object[] totalRow = new Object[SPEND_MONTHS + 1];
            totalRow[0] = "월별 총 지출 합계";
            for (int i = 0; i < SPEND_MONTHS; i++) {
                totalRow[i + 1] = String.format("%,d원", monthlyTotal);
            }
            spendModel.addRow(totalRow);
        }
    }

    private void saveSubscription() {
        String name = nameField.getText().trim();
        String dateText = dateField.getText().trim();
        String amountText = amountField.getText().trim();

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "서비스명을 입력하세요.");
            return;
        }

        LocalDate date;
        try {
            date = LocalDate.parse(dateText);
        } catch (DateTimeParseException e) {
            JOptionPane.showMessageDialog(this, "날짜를 yyyy-MM-dd 형식으로 입력하세요.");
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(amountText.replace(",", ""));
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "월 요금은 숫자로 입력하세요.");
            return;
        }

        String cancelUrl = LLMParser.getCancelUrl(name);
        SubscriptionDAO.insertSubscription(name, amount, date.toString(), cancelUrl);

        loadDataFromDB();
        clearForm();
        JOptionPane.showMessageDialog(this, "구독 정보가 저장되었습니다.");
    }

    private void deleteSubscription() {
        int row = listTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "삭제할 줄을 먼저 선택하세요.");
            return;
        }

        int answer = JOptionPane.showConfirmDialog(this, "선택한 구독을 삭제할까요?", "삭제 확인", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        String name = listModel.getValueAt(row, 0).toString();
        String date = listModel.getValueAt(row, 1).toString();

        SubscriptionDAO.deleteSubscription(name, date);
        loadDataFromDB();
    }

    private void clearForm() {
        nameField.setText("");
        dateField.setText("");
        amountField.setText("");
        listTable.clearSelection();
    }

    private void refreshDays() {
        loadDataFromDB();
    }
}