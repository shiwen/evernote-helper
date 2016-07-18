package me.shiwen.evernote.error;

public class EvernoteBackupException extends Exception {  // TODO what if called by outer classes?
    public EvernoteBackupException(Throwable t) {
        super(t.getMessage(), t);
    }

    public EvernoteBackupException(String message) {
        super(message);
    }
}
