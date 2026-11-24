package notification;

import discord.DiscordWebhook;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.net.URI;

public class Notification {

    private static TrayIcon trayIcon;

    public void sendAlert(String serviceName, String price, String endDate, String cancelUrl) {
        String tempPrice = price;
        try {
            tempPrice = String.format("%,d", Integer.parseInt(price.replace(",", "").trim()));
        } catch (Exception ignored) {
        }
        final String formattedPrice = tempPrice;
        final String safeUrl = (cancelUrl != null && !cancelUrl.trim().isEmpty()) ? cancelUrl.trim() : "";

        String msg = serviceName + " 결제일(" + endDate + ") 임박!\n"
                + "결제 예정 금액: " + formattedPrice + "원\n"
                + "해지 안내: " + (safeUrl.isEmpty() ? "링크 없음" : safeUrl);

        System.out.println("==========[알림]==========\n" + msg + "\n==========================");

        // 1. 사용자별 디스코드 웹훅으로 푸시 알림 전송 (설정된 경우 자동 전송)
        DiscordWebhook.sendAlertAsync(serviceName, formattedPrice, endDate, safeUrl);

        // 2. 윈도우 기본 우측 하단 토스트 알림 (별도 설정 없이 설치 즉시 작동)
        showWindowsTrayNotification(serviceName, formattedPrice, endDate);

        // 3. Swing 팝업 알림 (가로 크기 고정 + 해지 링크 열기/복사 버튼)
        SwingUtilities.invokeLater(() -> showCompactAlert(serviceName, formattedPrice, endDate, safeUrl));
    }

    private static synchronized void showWindowsTrayNotification(String serviceName, String formattedPrice, String endDate) {
        if (!SystemTray.isSupported()) {
            return;
        }
        try {
            if (trayIcon == null) {
                SystemTray tray = SystemTray.getSystemTray();
                BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(52, 152, 219));
                g.fillOval(1, 1, 14, 14);
                g.setColor(Color.WHITE);
                g.fillRect(7, 4, 2, 5);
                g.fillRect(7, 10, 2, 2);
                g.dispose();

                trayIcon = new TrayIcon(img, "구독 마감 알림");
                trayIcon.setImageAutoSize(true);
                tray.add(trayIcon);
            }
            trayIcon.displayMessage(
                    "구독 결제 임박 알림",
                    serviceName + " (" + endDate + ") - " + formattedPrice + "원 결제 예정",
                    TrayIcon.MessageType.WARNING
            );
        } catch (Exception e) {
            System.err.println("[윈도우 알림] 표시 실패: " + e.getMessage());
        }
    }

    private void showCompactAlert(String serviceName, String formattedPrice, String endDate, String cancelUrl) {
        String bodyText = serviceName + " 결제일(" + endDate + ") 임박!\n"
                + "결제 예정 금액: " + formattedPrice + "원";

        JTextArea textArea = new JTextArea(bodyText);
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
        textScroll.setPreferredSize(new Dimension(280, 52));

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        panel.add(textScroll, BorderLayout.CENTER);

        if (!cancelUrl.isEmpty()) {
            JTextField urlField = new JTextField(20);
            urlField.setText(cancelUrl);
            urlField.setEditable(false);
            urlField.setCaretPosition(0);
            urlField.setPreferredSize(new Dimension(280, 26));

            JButton openBtn = new JButton("해지 링크 열기");
            openBtn.addActionListener(e -> {
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(new URI(cancelUrl));
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, "브라우저를 열 수 없습니다.\n" + ex.getMessage());
                }
            });

            JButton copyBtn = new JButton("링크 복사");
            copyBtn.addActionListener(e -> {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(cancelUrl), null);
                copyBtn.setText("복사 완료!");
            });

            JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            btnRow.setOpaque(false);
            btnRow.add(openBtn);
            btnRow.add(copyBtn);

            JPanel linkBox = new JPanel(new BorderLayout(0, 6));
            linkBox.setOpaque(false);
            linkBox.add(new JLabel("해지 안내:"), BorderLayout.NORTH);
            linkBox.add(urlField, BorderLayout.CENTER);
            linkBox.add(btnRow, BorderLayout.SOUTH);

            panel.add(linkBox, BorderLayout.SOUTH);
        }

        JOptionPane optionPane = new JOptionPane(panel, JOptionPane.WARNING_MESSAGE);
        JDialog dialog = optionPane.createDialog(null, "구독 결제 알림");
        dialog.setAlwaysOnTop(true);
        dialog.pack();
        if (dialog.getWidth() > 400) {
            dialog.setSize(400, dialog.getHeight());
        }
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        dialog.dispose();
    }
}