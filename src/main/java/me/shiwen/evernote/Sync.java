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
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.w3c.dom.Document;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.evernote.edam.userstore.Constants.EDAM_VERSION_MAJOR;
import static com.evernote.edam.userstore.Constants.EDAM_VERSION_MINOR;

public class Sync {
    private static final String BASE_DIR = "notes";
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

    public List<Note> listNotes() throws EvernoteBackupException {
        try {
            if (!initialized) {
                init();  // TODO do not need to init
            }

            List<Note> allNotes = new ArrayList<>();

            NoteFilter filter = new NoteFilter();
            filter.setOrder(NoteSortOrder.CREATED.getValue());
            filter.setAscending(true);

            int offset = 0;
            boolean hasMoreNotes = true;
            do {
                try {
                    NoteList noteList = noteStore.findNotes(filter, offset, BATCH_MAX_SIZE);
                    List<Note> notes = noteList.getNotes();
                    if (notes == null) {
                        break;
                    }
                    for (Note note : notes) {
                        allNotes.add(note);
                        offset++;
                        System.out.println(offset + ": " + note.getGuid());  // TODO log (level debug)
                    }
                    hasMoreNotes = offset < noteList.getTotalNotes();
                } catch (EDAMSystemException e) {
                    processException(e);
                }
            } while (hasMoreNotes);

            return allNotes;
        } catch (EvernoteBackupException e) {
            throw e;
        } catch (Exception e) {
            throw new EvernoteBackupException(e);
        }
    }

    public boolean syncNotes(List<Note> notes, Git git) throws EvernoteBackupException {
        if (!initialized) {
            init();  // TODO what if init failed?
        }

        boolean needCommit = false;
        for (Note note : notes) {
            boolean modified = syncNote(note, git);
            if (modified) {
                needCommit = true;
            }
        }
        return needCommit;
    }

    public boolean syncNote(Note note, Git git) throws EvernoteBackupException {
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

            if (!localNote.equals(oldNote)) {
                saveToFile(guid, localNote);
                git.add().addFilepattern(guid).call();
                return true;
            } else {
                return false;
            }
        } catch (EDAMSystemException e) {
            processException(e);
            return syncNote(note, git);
        } catch (Exception e) {
            throw new EvernoteBackupException(e);
        }
    }

//    public void pullNotes(Git git) throws EvernoteBackupException {
//        try {
//            if (!initialized) {
//                init();
//            }
//
//            NoteFilter filter = new NoteFilter();
//            filter.setOrder(NoteSortOrder.CREATED.getValue());
//            filter.setAscending(true);
//
//            int offset = 0;
//            boolean hasMoreNotes;
//            List<String> guidList = new ArrayList<String>();
//            do {
//                try {
//                    NoteList noteList = noteStore.findNotes(filter, offset, BATCH_MAX_SIZE);
//                    List<Note> notes = noteList.getNotes();
//                    if (notes == null) {
//                        break;
//                    }
//                    for (Note note : notes) {
//                        guidList.add(note.getGuid());
//                        saveNote(note);
//                        offset++;
//                        System.out.println(offset + ": " + note.getGuid());
//                    }
//                    hasMoreNotes = offset < noteList.getTotalNotes();
//                } catch (EDAMSystemException e) {
//                    processException(e);
//                    hasMoreNotes = true;
//                }
//            } while (hasMoreNotes);
//
//            // remove deleted notes from repository
////            Stream<Path> stream = Files.list(Paths.get(BASE_DIR));
////            for (Path entry: stream) {
////                result.add(entry);
////            }
//            List<String> repositoryFiles = Files.list(Paths.get(BASE_DIR))
//                    .filter(Files::isRegularFile)
//                    .map(path -> path.getFileName().toString())
//                    .collect(Collectors.toList());
//            for (String file : repositoryFiles) {
//                if (!guidList.contains(file)) {
//                    Files.delete(Paths.get(BASE_DIR, file));
//                    git.rm().addFilepattern(file).call();
//                }
//            }
//        } catch (EvernoteBackupException e) {
//            throw e;
//        } catch (Exception e) {
//            throw new EvernoteBackupException(e);
//        }
//    }

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

    public boolean removeObsoleteNotes(List<Note> notes, Git git) throws IOException, GitAPIException {
        List<String> guids = notes.stream().map(Note::getGuid).collect(Collectors.toList());
        List<String> files = null;
        try {
            files = Files.list(Paths.get(BASE_DIR))
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toList());

            boolean needCommit = false;
            for (String file : files) {
                if (!guids.contains(file)) {
                    Files.delete(Paths.get(BASE_DIR, file));
                    git.rm().addFilepattern(file).call();
                    needCommit = true;
                }
            }
            return needCommit;
        } catch (IOException | GitAPIException e) {
            e.printStackTrace();
            throw e;
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
        return Paths.get(BASE_DIR, guid);
    }

    public void removeUnusedTags() {
        // TODO implement this
    }

    public static void main(String... args) throws Exception {

//        // check whether whitespaces in the original content affects the reformatted content
//        DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(BASE_DIR));
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

//        // git commit
//        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
//        repositoryBuilder.setMustExist(true);
//        repositoryBuilder.setGitDir(new File(BASE_DIR, ".git"));
//        Repository repository = repositoryBuilder.build();
//        Git git = new Git(repository);
//        git.add().addFilepattern(".").call();
//        git.commit().setMessage("The big bang!").call();

//        // some kind of debug
//        String content = new String(Files.readAllBytes(Paths.get("/home/shiwen/tt")));
//        Document document = XmlUtils.getDocument(content);
//        debug(document);

//        // check what strange characters are in the notes
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

        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
        repositoryBuilder.setMustExist(true);
        repositoryBuilder.setGitDir(new File(BASE_DIR, ".git"));
        Repository repository = repositoryBuilder.build();
        Git git = new Git(repository);

//        Sync e = new Sync("");
//        e.pullNotes(git);

        // 1. list all remote notes
        Sync e = new Sync("");
        List<Note> notes = e.listNotes();

        // 2. sync all remote notes
        boolean notesAdded = e.syncNotes(notes, git);

        // 3. delete notes that do not exist in remote repository
        boolean notesDeleted = e.removeObsoleteNotes(notes, git);

        // 4. commit changes to git
        if (notesAdded || notesDeleted) {
            git.commit().setMessage("Snapshot").call();
        }
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
