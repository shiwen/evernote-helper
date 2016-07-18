package me.shiwen.evernote.test;

import me.shiwen.evernote.EvernoteBackup;
import me.shiwen.evernote.error.EvernoteBackupException;

public class EvernoteHelper {
    private static final String TOKEN = "";

    public static void main(String... args) throws EvernoteBackupException {
        EvernoteBackup evernoteBackup = new EvernoteBackup(TOKEN);
        evernoteBackup.pullNotes();
    }
}
