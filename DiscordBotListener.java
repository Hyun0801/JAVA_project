package discord;

import database.SubscriptionDAO;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DiscordBotListener extends JFrame {

    private final JTextField nameField = new JTextField(10);
    private final JTextField dateField = new JTextField(10);
    private final JTextField amountField = new JTextField(7);

    private final String[] cols = {"서비스명", "종료/결제일", "남은 일수", "월 요금(원)"};
    private final DefaultTableModel listModel = new DefaultTableModel(cols, 0);
    private final JTable listTable = new JTable(listModel);

    private final DefaultTableModel spendModel = new DefaultTableModel();
    private final JTable spendTable = new JTable(spendModel);

    private final String[] notiCols = {"서비스명", "종료/결제일", "남은 일수", "알림"};
    private final DefaultTableModel notiModel = new DefaultTableModel(notiCols, 0);
    private final JTable notiTable = new JTable(notiModel);

    private final Set<String> poppedKeys = new HashSet<>();
    private final int alertDays = 3;

    public DiscordBotListener() {
        setTitle("구독 마감 알림");
        setSize(900, 620);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("구독 관리", makeManagerPanel());
        tabs.addTab("월별 지출", makeSpendPanel());
        tabs.addTab("알림", makeNotiPanel());

        add(tabs);
        refreshAll(true);
        setVisible(true);

        // 5초마다 D-Day 확인해서 팝업 띄우기
        Timer t = new Timer(5000, e -> refreshAll(true));
        t.start();
    }

    private JPanel makeManagerPanel() {
        JPanel p = new JPanel(new BorderLayout(8, 8));

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        form.add(new JLabel("서비스명"));
        form.add(nameField);
        form.add(new JLabel("종료일(yyyy-MM-dd)"));
        form.add(dateField);
        form.add(new JLabel("월 요금"));
        form.add(amountField);

        JButton saveBtn = new JButton("저장");
        JButton delBtn = new JButton("삭제");
        JButton refBtn = new JButton("새로고침");

        saveBtn.addActionListener(e -> saveItem());
        delBtn.addActionListener(e -> deleteItem());
        refBtn.addActionListener(e -> refreshAll(true));

        JPanel top = new JPanel(new BorderLayout());
        top.add(form, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btns.add(refBtn);
        btns.add(delBtn);
        btns.add(saveBtn);
        top.add(btns, BorderLayout.SOUTH);

        listTable.setRowHeight(25);
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(listTable), BorderLayout.CENTER);
        return p;
    }

    private JPanel makeSpendPanel() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.add(new JScrollPane(spendTable), BorderLayout.CENTER);
        return p;
    }

    private JPanel makeNotiPanel() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.add(new JScrollPane(notiTable), BorderLayout.CENTER);
        return p;
    }

    private void saveItem() {
        String name = nameField.getText().trim();
        String dateStr = dateField.getText().trim();
        String amtStr = amountField.getText().trim();

        if (name.isEmpty() || dateStr.isEmpty() || amtStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "서비스명, 종료일, 월 요금을 모두 입력하세요.");
            return;
        }

        try {
            LocalDate.parse(dateStr);
            int amt = Integer.parseInt(amtStr.replace(",", ""));
            SubscriptionDAO.insertSubscription(name, amt, dateStr, "");
            nameField.setText("");
            dateField.setText("");
            amountField.setText("");
            refreshAll(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "날짜는 yyyy-MM-dd, 요금은 숫자로 입력하세요.");
        }
    }

    private void deleteItem() {
        int row = listTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "삭제할 줄을 선택하세요.");
            return;
        }
        String name = listModel.getValueAt(row, 0).toString();
        String date = listModel.getValueAt(row, 1).toString();
        SubscriptionDAO.deleteSubscription(name, date);
        refreshAll(false);
    }

    public void refreshAll(boolean showPopup) {
        listModel.setRowCount(0);
        notiModel.setRowCount(0);

        List<String[]> rows = SubscriptionDAO.getAllSubscriptions();
        LocalDate today = LocalDate.now();
        StringBuilder alertMsg = new StringBuilder();

        for (String[] r : rows) {
            try {
                String name = r[0];
                String dateStr = r[1].trim();
                int amt = Integer.parseInt(r[2].trim());
                long days = ChronoUnit.DAYS.between(today, LocalDate.parse(dateStr));

                listModel.addRow(new Object[]{name, dateStr, days, amt});

                if (days >= 0 && days <= alertDays) {
                    String dDay = (days == 0) ? "D-Day" : "D-" + days;
                    String msg = "곧 구독 종료일입니다!";
                    notiModel.addRow(new Object[]{name, dateStr, dDay, msg});

                    String key = name + "_" + dateStr;
                    if (!poppedKeys.contains(key)) {
                        poppedKeys.add(key);
                        alertMsg.append("[").append(name).append(" | ").append(dDay).append("] ")
                                .append(msg).append("\n");
                    }
                }
            } catch (Exception ignored) {
            }
        }

        refreshSpend(rows);

        if (showPopup && alertMsg.length() > 0) {
            JOptionPane.showMessageDialog(this, alertMsg.toString().trim(), "알림", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void refreshSpend(List<String[]> rows) {
        YearMonth start = YearMonth.now();
        String[] headers = new String[7];
        headers[0] = "서비스";
        for (int i = 0; i < 6; i++) {
            headers[i + 1] = start.plusMonths(i).toString();
        }
        spendModel.setColumnIdentifiers(headers);
        spendModel.setRowCount(0);

        for (String[] r : rows) {
            try {
                String name = r[0];
                int amt = Integer.parseInt(r[2].trim());
                Object[] row = new Object[7];
                row[0] = name;
                for (int i = 0; i < 6; i++) {
                    row[i + 1] = String.format("%,d원", amt);
                }
                spendModel.addRow(row);
            } catch (Exception ignored) {
            }
        }
    }
}
