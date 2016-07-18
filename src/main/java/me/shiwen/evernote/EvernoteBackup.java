package me.shiwen.evernote;

import com.evernote.auth.EvernoteAuth;
import com.evernote.auth.EvernoteService;
import com.evernote.clients.ClientFactory;
import com.evernote.clients.NoteStoreClient;
import com.evernote.clients.UserStoreClient;
import com.evernote.edam.error.EDAMErrorCode;
import com.evernote.edam.error.EDAMNotFoundException;
import com.evernote.edam.error.EDAMSystemException;
import com.evernote.edam.error.EDAMUserException;
import com.evernote.edam.notestore.NoteFilter;
import com.evernote.edam.notestore.NoteList;
import com.evernote.edam.type.Note;
import com.evernote.edam.type.NoteAttributes;
import com.evernote.edam.type.NoteSortOrder;
import com.evernote.edam.type.Notebook;
import com.evernote.edam.type.Tag;
import com.evernote.thrift.TException;
import me.shiwen.evernote.error.EvernoteBackupException;
import me.shiwen.evernote.model.LocalNote;
import me.shiwen.evernote.utils.XmlUtils;
import me.shiwen.evernote.utils.YamlUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.evernote.edam.userstore.Constants.EDAM_VERSION_MAJOR;
import static com.evernote.edam.userstore.Constants.EDAM_VERSION_MINOR;

public class EvernoteBackup {
    private NoteStoreClient noteStore;
    private Map<String, String> notebookNameMap = new HashMap<>();
    private Map<String, String> tagNameMap = new HashMap<>();
    private int pulled = 0;

    public EvernoteBackup(String token) throws EvernoteBackupException {
        try {
            EvernoteAuth evernoteAuth = new EvernoteAuth(EvernoteService.SANDBOX, token);
            ClientFactory factory = new ClientFactory(evernoteAuth);
            UserStoreClient userStore = factory.createUserStoreClient();
            if (!userStore.checkVersion("", EDAM_VERSION_MAJOR, EDAM_VERSION_MINOR)) {
                throw new EvernoteBackupException("Incompatible Evernote client protocol version");
            }
            noteStore = factory.createNoteStoreClient();

            List<Notebook> notebooks = noteStore.listNotebooks();
            for (Notebook notebook : notebooks) {
                notebookNameMap.put(notebook.getGuid(), notebook.getName());
            }

            List<Tag> tags = noteStore.listTags();
            for (Tag tag : tags) {
                tagNameMap.put(tag.getGuid(), tag.getName());
            }
        } catch (Exception e) {
            throw new EvernoteBackupException(e);
        }
    }

    public void removeUnusedTags() {
        // TODO implement this
        // Map<String, Integer> tagCounts = noteCollectionCounts.getTagCounts();
        // sum = 0;
        // for (String tag : tagCounts.keySet()) {
        // System.out.println(tagNameMap.get(tag) + ": " + tagCounts.get(tag));
        // sum += tagCounts.get(tag);
        // }
        // System.out.println("total: " + sum);
    }

    public void pullNotes() throws EvernoteBackupException {
        try {
            NoteFilter filter = new NoteFilter();
            filter.setOrder(NoteSortOrder.CREATED.getValue());
            filter.setAscending(false);
            boolean hasMoreNotes;
            do {
                try {
                    hasMoreNotes = progressivelyPullNotes(filter);
                } catch (EDAMSystemException e) {
                    int pauseSeconds = e.getRateLimitDuration();
                    System.err.println("pause for " + pauseSeconds + " seconds");
                    Thread.sleep(pauseSeconds * 1000L);
                    hasMoreNotes = true;
                }
            } while (hasMoreNotes);
        } catch (Exception e) {
            throw new EvernoteBackupException(e);
        }
    }

    private boolean progressivelyPullNotes(NoteFilter filter) throws EDAMSystemException, EvernoteBackupException {
        try {
            NoteList noteList = noteStore.findNotes(filter, pulled, 50);
            int totalNotes = noteList.getTotalNotes();
            List<Note> notes = noteList.getNotes();

            for (Note note : notes) {
                NoteAttributes attributes = note.getAttributes();
                Note fullNote = noteStore.getNote(note.getGuid(), true, true, false, false);  // TODO resources

                LocalNote localNote = new LocalNote();
                localNote.title = note.getTitle();
                localNote.created = new Date(note.getCreated());
                localNote.updated = new Date(note.getUpdated());
                localNote.notebook = notebookNameMap.get(note.getNotebookGuid());
                localNote.reference = attributes.getSourceURL();
                localNote.content = XmlUtils.format(fullNote.getContent(), true).replaceAll("\r", "").trim();

                List<String> tagGuids = note.getTagGuids();
                if (tagGuids != null) {
                    List<String> tagNames = new ArrayList<>();
                    for (String tagGuid : tagGuids) {
                        tagNames.add(tagNameMap.get(tagGuid));
                    }
                    localNote.tags = tagNames.toArray(new String[0]);
                }
                pulled++;
                System.out.println("pull note " + pulled + ": " + note.getGuid());
            }
            return pulled < totalNotes;
        } catch (EDAMSystemException e) {
            if (e.getErrorCode() == EDAMErrorCode.RATE_LIMIT_REACHED) {
                throw e;
            } else {
                throw new EvernoteBackupException(e);
            }
        } catch (Exception e) {
            throw new EvernoteBackupException(e);
        }
    }

    private Path getPath(String guid) {
        return Paths.get("notes", guid);
    }

    private LocalNote loadFromFile(String guid) throws IOException {
        Path path = getPath(guid);
        if (Files.exists(path)) {
            return YamlUtils.load(new String(Files.readAllBytes(path)));
        } else {
            return null;
        }
    }

    //    private boolean contentUpdated(Note note, LocalNote localNote) {
    //        String contentHash = new String(note.getContentHash());
    //        return !contentHash.equals(localNote.hash);
    //    }

    private void updateNote(Note note, String oldContentHash) throws EvernoteBackupException {
        try {
            LocalNote localNote = new LocalNote();
            localNote.title = note.getTitle();
            localNote.created = new Date(note.getCreated());
            localNote.updated = new Date(note.getUpdated());
            localNote.version = note.getUpdateSequenceNum();
            localNote.hash = hexString(note.getContentHash());
            localNote.notebook = notebookNameMap.get(note.getNotebookGuid());
            localNote.reference = note.getAttributes().getSourceURL();

            List<String> tagGuids = note.getTagGuids();
            if (tagGuids != null) {
                List<String> tagNames = new ArrayList<>();
                for (String tagGuid : tagGuids) {
                    tagNames.add(tagNameMap.get(tagGuid));
                }
                localNote.tags = tagNames.toArray(new String[0]);
            }

            if (!localNote.hash.equals(oldContentHash)) {
                Note fullNote = noteStore.getNote(note.getGuid(), true, true, false, false);  // TODO resources
                localNote.content = XmlUtils.format(fullNote.getContent(), true).replaceAll("\r", "").trim();
            }
        } catch (Exception e) {
            throw new EvernoteBackupException(e);
        }
    }

    public void storeNotes(String guid, LocalNote localNote) throws IOException {
        Path path = getPath(guid);
        Files.write(path, YamlUtils.dump(localNote).getBytes());
    }

    private String hexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
