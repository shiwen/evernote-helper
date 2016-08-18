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
import org.w3c.dom.Document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.evernote.edam.userstore.Constants.EDAM_VERSION_MAJOR;
import static com.evernote.edam.userstore.Constants.EDAM_VERSION_MINOR;

public class Sync {
    private static final int BATCH_MAX_SIZE = 50;

    private ClientFactory factory;
    private NoteStoreClient noteStore;
    private boolean initialized = false;
    private Map<String, String> notebookNameMap = new HashMap<>();
    private Map<String, String> tagNameMap = new HashMap<>();

    public Sync(String token) throws EvernoteBackupException {
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
                        System.out.println(offset + ": " + note.getGuid());
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

    private void saveNote(Note note) throws EDAMSystemException, EvernoteBackupException {
        try {
            LocalNote localNote = new LocalNote();
            localNote.title = note.getTitle();
            localNote.notebook = notebookNameMap.get(note.getNotebookGuid());
            localNote.created = new Date(note.getCreated());
            localNote.updated = new Date(note.getUpdated());
            localNote.version = note.getUpdateSequenceNum();
            localNote.hash = hexString(note.getContentHash());
            localNote.reference = note.getAttributes().getSourceURL();

            List<String> tagGuids = note.getTagGuids();
            if (tagGuids != null) {
                List<String> tagNames = tagGuids.stream().map(tagGuid -> tagNameMap.get(tagGuid))
                        .collect(Collectors.toList());
                localNote.tags = tagNames.toArray(new String[0]);
            }

            String guid = note.getGuid();
            LocalNote oldNote = loadFromFile(guid);
            if (oldNote == null || !localNote.hash.equals(oldNote.hash)) {
                Note fullNote = noteStore.getNote(guid, true, true, false, false);  // TODO resources
                Document document = XmlUtils.getDocument(fullNote.getContent());
                localNote.verbose = XmlUtils.compress(document);
                localNote.content = XmlUtils.format(document, true).replace("\r", "");
            } else {
                localNote.verbose = oldNote.verbose;
                localNote.content = oldNote.content;
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
                throw new RuntimeException(ex);
            }
        } else {
            throw new EvernoteBackupException(e);
        }
    }

    private String hexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
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

    private Path getPath(String guid) {
        return Paths.get("notes", guid);
    }

    public void removeUnusedTags() {
        // TODO implement this
    }

    public static void main(String... args) throws Exception {

        //        EvernoteBackup e = new EvernoteBackup("S=s1:U=c47099:E=15d69125349:C=15611612390:P=1cd:A=en-devtoken:V=2:H=30e21dff02eca11eba459f34e575cd0e");
        //        e.pullNotes();

        //        DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("notes"));
        //        for (Path path : stream) {
        //            if (Files.isDirectory(path)) {
        //                continue;
        //            }
        //            String guid = path.getFileName().toString();
        //            System.out.println(guid);
        //            LocalNote note = YamlUtils.load(new String(Files.readAllBytes(path)));
        //            String content = new String(Files.readAllBytes(Paths.get("debug_content", guid)));
        //            content = content.substring(3, content.length() - 3);
        //            content = content.replaceAll(">\\s+<", "><");
        //            note.content = XmlUtils.format(content, true).trim();
        //            Files.write(path, YamlUtils.dump(note).getBytes());
        //        }
        //        stream.close();

        //        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
        //        repositoryBuilder.setMustExist(true);
        //        repositoryBuilder.setGitDir(new File("notes", ".git"));
        //        Repository repository = repositoryBuilder.build();
        //        Git git = new Git(repository);
        //        git.add().addFilepattern(".").call();
        //        git.commit().setMessage("The big bang!").call();

        //        String content = new String(Files.readAllBytes(Paths.get("/home/shiwen/tt")));
        //        Document document = XmlUtils.getDocument(content);
        //        debug(document);

//        Pattern pattern = Pattern.compile("[^\\p{IsHan}\\p{L}\\p{Punct}\\p{L}\n" +
//                " \\p{Nd}\\p{Pc}\\p{InCJK_Symbols_and_Punctuation}\\p{InGeneral_Punctuation}\\s]");
//        //        Pattern pattern = Pattern.compile("\\p{Cf}");
//        DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("debug_content"));
//        Set<Character> charSet = new HashSet<>();
//        for (Path path : stream) {
//            if (Files.isDirectory(path)) {
//                continue;
//            }
//            String guid = path.getFileName().toString();
//            System.out.println(guid);
//            String a = new String(Files.readAllBytes(path));
//            //        System.out.println(String.format("\\u%04x", (int)(a.charAt(0))));
//            Matcher m = pattern.matcher(a);
//            while (m.find()) {
//                charSet.add(m.group().charAt(0));
//            }
//        }
//        BufferedWriter writer = Files.newBufferedWriter(Paths.get("charset"), Charset.defaultCharset());
//        for (char c : charSet) {
//            writer.write("->" + c + "<- " + String.format("\\u%04x", (int)c) + "\n");
//        }
//        writer.close();

        Sync e = new Sync("S=s1:U=c47099:E=15df547b72e:C=1569d968a80:P=1cd:A=en-devtoken:V=2:H=e6267dc3de9b3d0a24c27149b54dac0e");
        e.pullNotes();
    }

//    public static void debug(Node node) {
//        NodeList children = node.getChildNodes();
//        for (int i = 0; i < children.getLength(); i++) {
//            Node child = children.item(i);
//            if (child.getNodeType() == Node.TEXT_NODE) {
//                System.out.println("text: ###" + child.getTextContent() + "###");
//            } else {
//                System.out.println("element " + child.getNodeName());
//                debug(child);
//            }
//        }
//    }
}