package discord;

import api.LLMParser;
import database.SubscriptionDAO;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.awt.Color;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiscordBotListener extends ListenerAdapter {

    private static volatile JDA jda;
    private static volatile boolean isReady = false;
    private static volatile String targetChannelId = "";
    private static volatile String targetUserId = "";
    private static final Queue<String> pendingMessages = new ConcurrentLinkedQueue<>();

    private static final Pattern URL_PATTERN = Pattern.compile("(https?://[^\\s<>\"'\\)]+)");

    // MainClass에서 호출해 백그라운드로 디스코드 봇 실행
    public static void startBot() {
        new Thread(() -> {
            try {
                Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
                String token = dotenv.get("DISCORD_TOKEN");
                String envChannelId = dotenv.get("DISCORD_CHANNEL_ID");
                String envUserId = dotenv.get("DISCORD_USER_ID");

                if (envChannelId != null && !envChannelId.trim().isEmpty()) {
                    targetChannelId = envChannelId.trim();
                }
                if (envUserId != null && !envUserId.trim().isEmpty()) {
                    targetUserId = envUserId.trim();
                }

                if (token == null || token.trim().isEmpty()) {
                    System.out.println("[디스코드] .env에 DISCORD_TOKEN이 없어 봇 실행을 건너뜁니다.");
                    return;
                }

                JDA builtJda = JDABuilder.createDefault(token.trim())
                        .enableIntents(
                                GatewayIntent.GUILD_MESSAGES,
                                GatewayIntent.DIRECT_MESSAGES,
                                GatewayIntent.MESSAGE_CONTENT
                        )
                        .addEventListeners(new DiscordBotListener())
                        .build();

                builtJda.awaitReady();
                jda = builtJda;
                isReady = true;
                System.out.println("[디스코드] 챗봇 로그인 성공! 봇 이름: " + jda.getSelfUser().getName());

                if (!hasTarget()) {
                    System.out.println("[디스코드] 안내: .env에 DISCORD_CHANNEL_ID 또는 DISCORD_USER_ID가 비어 있습니다. 디스코드 채널에서 아무 채팅(예: !도움말)을 한 번 입력하면 자동 연결됩니다.");
                }

                // 로그인 완료 전 쌓여 있던 알림 즉시 전송
                flushPendingMessages();
            } catch (Exception e) {
                System.err.println("[디스코드] 봇 실행 실패: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private static boolean hasTarget() {
        return (targetChannelId != null && !targetChannelId.isEmpty())
                || (targetUserId != null && !targetUserId.isEmpty());
    }

    // 알림 대상이 비어 있을 때 마지막 대화 채널/사용자로 자동 설정
    public static void setDefaultTarget(String channelId, String userId) {
        boolean updated = false;
        if ((targetChannelId == null || targetChannelId.isEmpty()) && channelId != null && !channelId.isEmpty()) {
            targetChannelId = channelId;
            updated = true;
        }
        if ((targetUserId == null || targetUserId.isEmpty()) && userId != null && !userId.isEmpty()) {
            targetUserId = userId;
            updated = true;
        }
        if (updated) {
            flushPendingMessages();
        }
    }

    // 결제 임박 알림을 디스코드로 전송 (Notification.java에서 호출)
    public static void sendNotification(String message) {
        if (!isReady || jda == null || !hasTarget()) {
            pendingMessages.add(message);
            return;
        }
        sendNow(message);
    }

    private static void flushPendingMessages() {
        if (!isReady || jda == null || !hasTarget()) {
            return;
        }
        while (!pendingMessages.isEmpty()) {
            String msg = pendingMessages.poll();
            if (msg != null) {
                sendNow(msg);
            }
        }
    }

    // 팝업 배너용 멘션 텍스트 + Embed 카드 + 클릭 버튼 전송
    private static void sendNow(String message) {
        if (jda == null || !hasTarget()) {
            return;
        }

        try {
            String url = extractFirstUrl(message);
            String[] lines = message.split("\\R");
            final String summaryLine = (lines.length > 0 && !lines[0].trim().isEmpty())
                    ? lines[0].trim() : "구독 결제/마감 임박 알림";

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🔔 구독 결제/마감 임박 알림")
                    .setDescription(message)
                    .setColor(new Color(231, 76, 60))
                    .setFooter("아래 버튼을 눌러 바로 해지하거나 지출 현황을 확인하세요.");

            List<Button> buttons = new ArrayList<>();
            if (isValidButtonUrl(url)) {
                buttons.add(Button.link(url, "🔗 해지 페이지 바로가기"));
            }
            buttons.add(Button.primary("sub_summary", "📊 월별 지출 요약"));
            buttons.add(Button.secondary("sub_list", "📋 전체 구독 목록"));

            // 1) 채널 ID가 있으면 채널에 멘션 포함 푸시 전송
            if (targetChannelId != null && !targetChannelId.isEmpty()) {
                TextChannel channel = jda.getTextChannelById(targetChannelId);
                if (channel != null) {
                    String mention = (targetUserId != null && !targetUserId.isEmpty())
                            ? "<@" + targetUserId + "> "
                            : "@everyone ";
                    String popupBannerText = mention + "🔔 " + summaryLine;

                    channel.sendMessage(popupBannerText)
                            .setEmbeds(embed.build())
                            .addComponents(ActionRow.of(buttons))
                            .queue(
                                    ok -> System.out.println("[디스코드] 채널 푸시 알림 전송 성공: " + summaryLine),
                                    err -> System.err.println("[디스코드] 채널 알림 전송 실패(봇 권한 확인 필요): " + err.getMessage())
                            );
                    return;
                } else {
                    System.err.println("[디스코드] 채널을 찾을 수 없습니다. DISCORD_CHANNEL_ID(" + targetChannelId + ")를 확인해 주세요.");
                }
            }

            // 2) 채널 ID가 없거나 못 찾았고 사용자 ID가 있으면 개인 DM으로 즉시 푸시 전송
            if (targetUserId != null && !targetUserId.isEmpty()) {
                final String uid = targetUserId;
                jda.retrieveUserById(uid).queue(
                        user -> user.openPrivateChannel().queue(
                                dm -> dm.sendMessage("🔔 " + summaryLine)
                                        .setEmbeds(embed.build())
                                        .addComponents(ActionRow.of(buttons))
                                        .queue(
                                                ok -> System.out.println("[디스코드] DM 푸시 알림 전송 성공: " + summaryLine),
                                                err -> System.err.println("[디스코드] DM 전송 실패(서버 멤버 DM 허용 확인): " + err.getMessage())
                                        ),
                                err -> System.err.println("[디스코드] DM 채널 열기 실패: " + err.getMessage())
                        ),
                        err -> System.err.println("[디스코드] DISCORD_USER_ID(" + uid + ") 사용자를 찾지 못했습니다.")
                );
            }
        } catch (Exception e) {
            System.err.println("[디스코드] 알림 전송 실패: " + e.getMessage());
        }
    }

    // 사용자가 디스코드 버튼을 클릭했을 때 처리
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();

        if (id.equals("sub_summary")) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("📊 월별 구독 지출 요약")
                    .setDescription(LLMParser.getMonthlySummary())
                    .setColor(new Color(52, 152, 219));

            event.replyEmbeds(embed.build())
                    .addComponents(ActionRow.of(Button.secondary("sub_list", "📋 전체 구독 목록 보기")))
                    .queue();
        } else if (id.equals("sub_list")) {
            event.replyEmbeds(buildListEmbed().build())
                    .addComponents(ActionRow.of(Button.primary("sub_summary", "📊 월별 지출 요약 보기")))
                    .queue();
        }
    }

    // 채팅 명령어 처리
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String chId = event.isFromGuild() ? event.getChannel().getId() : "";
        setDefaultTarget(chId, event.getAuthor().getId());

        String msg = event.getMessage().getContentRaw().trim();

        // 1. 자연어 자동 등록
        if (msg.startsWith("!등록")) {
            String userInput = msg.substring(3).trim();
            if (userInput.isEmpty()) {
                event.getChannel().sendMessage("사용법: `!등록 넷플릭스 프리미엄 17,000원 다음 달 5일 결제`").queue();
                return;
            }

            event.getChannel().sendMessage("⏳ 구독 정보를 분석해 DB에 저장하고 있습니다...").queue();

            new Thread(() -> {
                String result = LLMParser.parseAndSave(userInput);
                String url = extractFirstUrl(result);

                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("구독 등록 결과")
                        .setDescription(result)
                        .setColor(result.startsWith("✅") ? new Color(46, 204, 113) : new Color(231, 76, 60));

                List<Button> buttons = new ArrayList<>();
                if (isValidButtonUrl(url)) {
                    buttons.add(Button.link(url, "🔗 해지 페이지 바로가기"));
                }
                buttons.add(Button.primary("sub_summary", "📊 월별 지출 요약"));
                buttons.add(Button.secondary("sub_list", "📋 전체 구독 목록"));

                event.getChannel().sendMessageEmbeds(embed.build())
                        .addComponents(ActionRow.of(buttons))
                        .queue();
            }).start();
        }
        // 2. 월별 지출 요약 조회
        else if (msg.equals("!요약")) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("📊 월별 구독 지출 요약")
                    .setDescription(LLMParser.getMonthlySummary())
                    .setColor(new Color(52, 152, 219));

            event.getChannel().sendMessageEmbeds(embed.build())
                    .addComponents(ActionRow.of(Button.secondary("sub_list", "📋 전체 구독 목록")))
                    .queue();
        }
        // 3. 등록된 구독 목록 및 클릭 가능한 해지 링크 조회
        else if (msg.equals("!목록")) {
            event.getChannel().sendMessageEmbeds(buildListEmbed().build())
                    .addComponents(ActionRow.of(Button.primary("sub_summary", "📊 월별 지출 요약")))
                    .queue();
        }
        // 4. 명령어 안내 (바로 누를 수 있는 버튼 포함)
        else if (msg.equals("!도움말")) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("📌 구독 관리 챗봇 안내")
                    .setDescription(
                            "• `!등록 <문장>` : 자연어로 구독 자동 등록\n"
                                    + "   (예: `!등록 넷플릭스 프리미엄 17,000원 다음 달 5일 결제`)\n"
                                    + "• `!요약` : 월별 총 지출액 요약 보기\n"
                                    + "• `!목록` : 구독별 남은 일수 및 클릭 가능한 해지 링크 보기\n"
                                    + "• `!도움말` : 명령어 안내\n\n"
                                    + "아래 버튼을 눌러 바로 조회할 수도 있습니다."
                    )
                    .setColor(new Color(155, 89, 182));

            event.getChannel().sendMessageEmbeds(embed.build())
                    .addComponents(ActionRow.of(
                            Button.primary("sub_summary", "📊 월별 지출 요약"),
                            Button.secondary("sub_list", "📋 전체 구독 목록")
                    ))
                    .queue();
        }
    }

    private EmbedBuilder buildListEmbed() {
        List<String[]> list = SubscriptionDAO.getAllSubscriptions();
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📋 등록된 구독 및 해지 안내")
                .setColor(new Color(46, 204, 113));

        if (list.isEmpty()) {
            embed.setDescription("현재 등록된 구독 서비스가 없습니다.");
            return embed;
        }

        StringBuilder sb = new StringBuilder();
        LocalDate today = LocalDate.now();

        for (String[] sub : list) {
            String name = sub[0];
            String dateStr = sub[1];
            int price = 0;
            try {
                price = Integer.parseInt(sub[2].replace(",", "").trim());
            } catch (Exception ignored) {
            }
            String cancelUrl = (sub.length > 3 && sub[3] != null && !sub[3].isEmpty())
                    ? sub[3] : LLMParser.getCancelUrl(name);

            String dDayText = "";
            try {
                long days = ChronoUnit.DAYS.between(today, LocalDate.parse(dateStr.trim()));
                dDayText = (days == 0) ? "D-Day" : (days > 0 ? "D-" + days : "만료됨");
            } catch (Exception ignored) {
            }

            String linkMarkdown = isValidButtonUrl(cancelUrl)
                    ? "[해지 페이지 열기](" + cancelUrl + ")"
                    : cancelUrl;

            sb.append(String.format("• **%s** | %,d원 | %s (%s)\n  ↳ %s\n",
                    name, price, dateStr, dDayText, linkMarkdown));
        }

        String desc = sb.toString();
        if (desc.length() > 4000) {
            desc = desc.substring(0, 3990) + "\n...(이하 생략)";
        }
        embed.setDescription(desc);
        return embed;
    }

    private static String extractFirstUrl(String text) {
        if (text == null) return null;
        Matcher m = URL_PATTERN.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static boolean isValidButtonUrl(String url) {
        return url != null
                && (url.startsWith("http://") || url.startsWith("https://"))
                && url.length() <= 512;
    }
}