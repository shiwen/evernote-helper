package me.shiwen.evernote;

import com.evernote.auth.EvernoteAuth;
import com.evernote.auth.EvernoteService;
import com.evernote.clients.ClientFactory;
import com.evernote.clients.NoteStoreClient;
import com.evernote.clients.UserStoreClient;
import com.evernote.edam.error.EDAMErrorCode;
import com.evernote.edam.error.EDAMSystemException;
import com.evernote.edam.notestore.NoteFilter;
import com.evernote.edam.notestore.NoteList;
import com.evernote.edam.type.Note;
import com.evernote.edam.type.NoteSortOrder;
import com.evernote.edam.type.Notebook;
import com.evernote.edam.type.Tag;
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
    private static final int BATCH_MAX_SIZE = 50;

    private ClientFactory factory;
    private NoteStoreClient noteStore;
    private boolean initialized = false;
    private Map<String, String> notebookNameMap = new HashMap<>();
    private Map<String, String> tagNameMap = new HashMap<>();

    public EvernoteBackup(String token) throws EvernoteBackupException {
        try {
            factory = new ClientFactory(new EvernoteAuth(EvernoteService.YINXIANG, token));
            UserStoreClient userStore = factory.createUserStoreClient();
            if (!userStore.checkVersion("", EDAM_VERSION_MAJOR, EDAM_VERSION_MINOR)) {
                throw new EvernoteBackupException("Incompatible Evernote client protocol version");
            }
        } catch (EvernoteBackupException e) {
            throw e;
        } catch (Exception e) {
            throw new EvernoteBackupException(e);
        }
    }

    public static void main(String... args) throws EvernoteBackupException {
        EvernoteBackup e = new EvernoteBackup("");
        e.pullNotes();
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

    private void init() throws EvernoteBackupException {
        boolean noteStoreInitialized = false;
        boolean notebookNameMapInitialized = false;
        while (!initialized) {
            try {
                if (!noteStoreInitialized) {
                    noteStore = factory.createNoteStoreClient();
                    noteStoreInitialized = true;
                }

                if (!notebookNameMapInitialized) {
                    List<Notebook> notebooks = noteStore.listNotebooks();
                    for (Notebook notebook : notebooks) {
                        notebookNameMap.put(notebook.getGuid(), notebook.getName());
                    }
                    notebookNameMapInitialized = true;
                }

                List<Tag> tags = noteStore.listTags();
                for (Tag tag : tags) {
                    tagNameMap.put(tag.getGuid(), tag.getName());
                }

                initialized = true;
            } catch (EDAMSystemException e) {
                processException(e);
            } catch (Exception e) {
                throw new EvernoteBackupException(e);
            }
        }
    }

    private String hexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
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

    private void saveToFile(String guid, LocalNote localNote) throws IOException {
        Path path = getPath(guid);
        Files.write(path, YamlUtils.dump(localNote).getBytes());
    }

    private void saveNote(Note note) throws EDAMSystemException, EvernoteBackupException {
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

            String guid = note.getGuid();
            LocalNote oldNote = loadFromFile(guid);
            if (oldNote == null || oldNote.content == null || !localNote.hash.equals(oldNote.hash)) {
                Note fullNote = noteStore.getNote(guid, true, true, false, false);  // TODO resources
                localNote.content = XmlUtils.format(fullNote.getContent(), true).replaceAll("\r", "").trim();
                System.err.println(fullNote.getContent());
            }

            saveToFile(guid, localNote);
        } catch (EDAMSystemException e) {
            throw e;
        } catch (Exception e) {
            throw new EvernoteBackupException(e);
        }
    }

    private void processException(EDAMSystemException e) throws EvernoteBackupException {
        if (e.getErrorCode() == EDAMErrorCode.RATE_LIMIT_REACHED) {
            int pauseSeconds = e.getRateLimitDuration();
            System.err.println("pause for " + pauseSeconds + " seconds");
            try {
                Thread.sleep(pauseSeconds * 1000L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        } else {
            throw new EvernoteBackupException(e);
        }
    }

    public void pullNotes() throws EvernoteBackupException {
        try {
            if (!initialized) {
                init();
            }

            NoteFilter filter = new NoteFilter();
            filter.setOrder(NoteSortOrder.CREATED.getValue());
            filter.setAscending(true);

            int offset = 0;
            boolean hasMoreNotes;
            do {
                try {
                    NoteList noteList = noteStore.findNotes(filter, offset, BATCH_MAX_SIZE);
                    List<Note> notes = noteList.getNotes();
                    if (notes == null) {
                        break;
                    }
                    for (Note note : notes) {
                        saveNote(note);
                        offset++;
                        System.out.println("note " + offset + ": " + note.getGuid());
                    }
                    hasMoreNotes = offset < noteList.getTotalNotes();
                } catch (EDAMSystemException e) {
                    processException(e);
                    hasMoreNotes = true;
                }
            } while (hasMoreNotes);
        } catch (EvernoteBackupException e) {
            throw e;
        } catch (Exception e) {
            throw new EvernoteBackupException(e);
        }
    }
}
