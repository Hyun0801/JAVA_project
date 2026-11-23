package discord;

import database.SQLiteManager;
import gui.DiscordBotListener;

import javax.swing.SwingUtilities;

public class Discord {
    public static void main(String[] args) {
        SQLiteManager.createDatabaseAndTable();
        SwingUtilities.invokeLater(DiscordBotListener::new);
    }
}
