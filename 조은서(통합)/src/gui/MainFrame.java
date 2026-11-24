package gui;

import api.LLMParser;
import database.SubscriptionDAO;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import discord.DiscordWebhook;

public class MainFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    // AI 자연어 자동 등록 컴포넌트
    JTextField aiInputField = new JTextField(38);
    JButton aiRegisterBtn = new JButton("AI 자동 등록");

    // 직접 입력 컴포넌트
    JTextField nameField = new JTextField(10);     // 서비스명
    JTextField dateField = new JTextField(10);     // 종료/결제일
    JTextField amountField = new JTextField(7);    // 월 요금

    // 구독 목록 표 (셀 직접 수정 방지)
    String[] columns = {"서비스명", "종료일", "남은 일수", "월 요금(원)"};
    public DefaultTableModel listModel = new DefaultTableModel(columns, 0) {
        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }
    };
    public JTable listTable = new JTable(listModel);

    // 월별 지출 표
    public DefaultTableModel spendModel;
    public JTable spendTable;
    int SPEND_MONTHS = 6;

    // 알림 표
    public DefaultTableModel notiModel;
    public JTable notiTable;
    int END_DAYS = 31;

    public MainFrame() {
        Container ct = getContentPane();
        setTitle("구독 서비스 결제 및 무료 체험 마감 알림");
        setSize(900, 660);

        // 월 요금 입력칸: 숫자 및 백스페이스/삭제 키만 허용
        amountField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if (!Character.isDigit(c) && c != KeyEvent.VK_BACK_SPACE && c != KeyEvent.VK_DELETE) {
                    e.consume();
                }
            }
        });

        // 탭 구성
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("구독 관리", ManagerPanel());
        tabs.addTab("월별 지출", SpendPanel());
        tabs.addTab("알림", NotificationPanel());
        tabs.addTab("알림 설정", SettingsPanel());

        // 디스코드 챗봇이나 AI 등록으로 DB가 바뀌면 Swing 표를 즉시 자동 갱신
        LLMParser.setOnDataUpdated(() -> SwingUtilities.invokeLater(this::loadDataFromDB));

        // 초기 DB 데이터 불러오기 (목록 + 지출 + 알림 일괄 갱신)
        loadDataFromDB();

        // 1분(60,000ms)마다 날짜(남은 일수), 월별 지출, 알림 표 자동 새로고침
        new javax.swing.Timer(60000, e -> refreshDays()).start();

        ct.add(tabs);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // DB에서 전체 구독 목록을 불러와 모든 탭(관리/지출/알림) 갱신
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

    // [탭 1] 구독 관리 패널
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
        form.add(new JLabel("종료일(yyyy-MM-dd)"));
        form.add(dateField);
        form.add(new JLabel("월 요금(원)"));
        form.add(amountField);

        listTable.setRowHeight(26);

        // 버튼 구성 및 이벤트 연결
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
                showWrappedMessage("AI 자동 등록 결과", resultMsg);
            });
        }).start();
    }

    // 긴 URL이 있어도 창 크기를 420px 이하로 고정하고, 링크 열기/복사 버튼을 제공하는 팝업
    private void showWrappedMessage(String title, String message) {
        String safeMsg = (message == null) ? "" : message;
        String extractedUrl = null;
        String displayMsg = safeMsg;

        Matcher m = Pattern.compile("(https?://[^\\s]+)").matcher(safeMsg);
        if (m.find()) {
            extractedUrl = m.group(1);
            // 긴 주소 줄은 본문에서 빼고 아래 전용 칸에 깔끔하게 표시
            displayMsg = safeMsg.replaceAll("(?m)^.*해지\\s*링크.*\\R?", "").trim();
            if (displayMsg.contains(extractedUrl)) {
                displayMsg = displayMsg.replace(extractedUrl, "").trim();
            }
        }

        JTextArea textArea = new JTextArea(displayMsg);
        textArea.setEditable(false);
        textArea.setOpaque(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        Font font = UIManager.getFont("OptionPane.messageFont");
        if (font != null) {
            textArea.setFont(font);
        }

        JScrollPane textScroll = new JScrollPane(textArea);
        textScroll.setBorder(null);
        textScroll.setOpaque(false);
        textScroll.getViewport().setOpaque(false);
        textScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        textScroll.setPreferredSize(new Dimension(300, 95));

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        panel.add(textScroll, BorderLayout.CENTER);

        // 해지 링크가 있을 때: 고정 폭 입력칸 + 열기/복사 버튼
        if (extractedUrl != null) {
            final String finalUrl = extractedUrl;

            JTextField urlField = new JTextField(22);
            urlField.setText(finalUrl);
            urlField.setEditable(false);
            urlField.setCaretPosition(0);
            urlField.setPreferredSize(new Dimension(300, 26));

            JButton openBtn = new JButton("해지 링크 열기");
            openBtn.addActionListener(e -> {
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(new URI(finalUrl));
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "브라우저를 열 수 없습니다.\n" + ex.getMessage());
                }
            });

            JButton copyBtn = new JButton("링크 복사");
            copyBtn.addActionListener(e -> {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(finalUrl), null);
                copyBtn.setText("복사 완료!");
            });

            JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            btnRow.setOpaque(false);
            btnRow.add(openBtn);
            btnRow.add(copyBtn);

            JPanel linkBox = new JPanel(new BorderLayout(0, 6));
            linkBox.setOpaque(false);
            linkBox.add(new JLabel("해지 링크:"), BorderLayout.NORTH);
            linkBox.add(urlField, BorderLayout.CENTER);
            linkBox.add(btnRow, BorderLayout.SOUTH);

            panel.add(linkBox, BorderLayout.SOUTH);
        }

        JOptionPane optionPane = new JOptionPane(panel, JOptionPane.INFORMATION_MESSAGE);
        JDialog dialog = optionPane.createDialog(this, title);
        dialog.pack();
        if (dialog.getWidth() > 420) {
            dialog.setSize(420, dialog.getHeight());
        }
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        dialog.dispose();
    }

    // [탭 2] 월별 지출 패널
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

    // [탭 3] 알림 패널
    private JPanel NotificationPanel() {
        JPanel np = new JPanel(new BorderLayout(8, 8));

        String[] notiColumns = {"서비스명", "종료일", "남은 일수", "알림"};
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

    // 알림 표 갱신 (D-Day ~ D-31 오름차순 표시)
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

    // 월별 지출 표 갱신 (향후 6개월 + 하단 총합계 행)
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

    // [저장] 직접 입력한 구독 정보를 DB에 저장 후 화면 갱신
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
            JOptionPane.showMessageDialog(this, "날짜를 2026-11-30 (yyyy-MM-dd) 형식으로 입력하세요.");
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

    // [삭제] 표에서 선택한 줄을 DB와 화면에서 모두 삭제
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

    // [새로 입력] 입력칸 초기화 및 표 선택 해제
    private void clearForm() {
        nameField.setText("");
        dateField.setText("");
        amountField.setText("");
        listTable.clearSelection();
    }

    // [새로고침 / 1분 자동 타이머] DB 데이터와 오늘 기준 남은 일수를 다시 계산
    private void refreshDays() {
        loadDataFromDB();
    }
 // [탭 4] 사용자별 디스코드 웹훅 알림 설정 패널
    private JPanel SettingsPanel() {
        JPanel sp = new JPanel(new BorderLayout(10, 10));
        sp.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JTextArea guide = new JTextArea(
                "📱 스마트폰 푸시 알림 연동 방법 (디스코드 웹훅)\n"
                + "1. 본인 디스코드 서버(혼자 쓰는 개인 서버 추천)에서 알림 받을 채널의 [톱니바퀴(채널 편집)] 클릭\n"
                + "2. [연동] → [웹훅] → [새 웹훅] 생성 후 [웹훅 URL 복사] 클릭\n"
                + "3. 아래 칸에 붙여넣고 [저장]을 누른 뒤 [테스트 알림 보내기]로 확인하세요.\n"
                + "   (웹훅을 입력하지 않아도 윈도우 기본 알림과 팝업은 정상 작동합니다.)"
        );
        guide.setEditable(false);
        guide.setOpaque(false);
        guide.setLineWrap(true);
        guide.setWrapStyleWord(true);

        JTextField webhookField = new JTextField(DiscordWebhook.loadWebhookUrl(), 45);
        JButton saveBtn = new JButton("저장");
        JButton testBtn = new JButton("테스트 알림 보내기");

        saveBtn.addActionListener(e -> {
            try {
                DiscordWebhook.saveWebhookUrl(webhookField.getText());
                JOptionPane.showMessageDialog(this, "디스코드 웹훅 설정이 저장되었습니다.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "설정 저장 실패: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            }
        });

        testBtn.addActionListener(e -> {
            String url = webhookField.getText().trim();
            testBtn.setEnabled(false);
            testBtn.setText("전송 중...");
            new Thread(() -> {
                String result = DiscordWebhook.sendTestMessage(url);
                SwingUtilities.invokeLater(() -> {
                    testBtn.setEnabled(true);
                    testBtn.setText("테스트 알림 보내기");
                    JOptionPane.showMessageDialog(this, result, "테스트 결과", JOptionPane.INFORMATION_MESSAGE);
                });
            }).start();
        });

        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        inputPanel.setBorder(BorderFactory.createTitledBorder("디스코드 웹훅 URL"));
        inputPanel.add(webhookField);
        inputPanel.add(saveBtn);
        inputPanel.add(testBtn);

        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.add(guide, BorderLayout.NORTH);
        top.add(inputPanel, BorderLayout.CENTER);

        sp.add(top, BorderLayout.NORTH);
        return sp;
    }
}