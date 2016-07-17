package me.shiwen.evernote;

import com.evernote.auth.EvernoteAuth;
import com.evernote.auth.EvernoteService;
import com.evernote.clients.ClientFactory;
import com.evernote.clients.NoteStoreClient;
import com.evernote.clients.UserStoreClient;
import com.evernote.edam.notestore.NoteFilter;
import com.evernote.edam.notestore.NoteList;
import com.evernote.edam.type.Note;
import com.evernote.edam.type.NoteAttributes;
import com.evernote.edam.type.NoteSortOrder;
import com.evernote.edam.type.Notebook;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

public class EvernoteHelper {
    private static final String TOKEN = "";

    public static void main(String... args) throws Exception {
        EvernoteAuth evernoteAuth = new EvernoteAuth(EvernoteService.YINXIANG, TOKEN);
        ClientFactory factory = new ClientFactory(evernoteAuth);
        NoteStoreClient noteStore = factory.createNoteStoreClient();

        List<Notebook> notebooks = noteStore.listNotebooks();
        Map<String, String> notebookMap = new HashMap<>();
        for (Notebook notebook : notebooks) {
            notebookMap.put(notebook.getGuid(), notebook.getName());
        }
        Note n = noteStore.getNote("885dc400-1b3d-4c18-a102-a78ef7b56216", true, true, false, false);
        System.out.println(n.getContent());
        System.out.println(n.getTagNames());
        System.out.println(n.getUpdated());
        System.out.println(n.getCreated());
        System.out.println(noteStore.getNoteTagNames("885dc400-1b3d-4c18-a102-a78ef7b56216"));
//        NoteFilter filter = new NoteFilter();
//        filter.setOrder(NoteSortOrder.CREATED.getValue());
//        filter.setAscending(true);
//        NoteList noteList = noteStore.findNotes(filter, 1100, 100);
//        List<Note> notes = noteList.getNotes();
//        for (Note note : notes) {
//            EnNote enNote = new EnNote();
//            NoteAttributes attributes = note.getAttributes();
//            enNote.title = note.getTitle();
//            enNote.author = attributes.getAuthor();
//            enNote.created = new Date(note.getCreated());
//            enNote.updated = new Date(note.getUpdated());
//            enNote.notebook = notebookMap.get(note.getNotebookGuid());
//            enNote.tags = note.getTagGuids() == null ? null : note.getTagGuids().toArray(new String[0]);
//            enNote.reference = attributes.getSourceURL();
////            System.out.println(" * " + note.getTitle());
//            Note fullNote = noteStore.getNote(note.getGuid(), true, true, false, false);
//            // TODO resources
//            enNote.content = XmlUtils.format(XmlUtils.getDocument(fullNote.getContent()), true).replaceAll("\r", "").trim();
////            System.out.println(enNote.content);
//            System.out.println(note.getGuid());
////            System.out.println(fullNote.getAttributes());
//
//            Path file = Paths.get("notes", note.getGuid());
//            System.out.println("###" + getYAML(enNote) + "###");
//            Files.write(file, getYAML(enNote).getBytes());
//        }
//        System.out.println(noteList.getTotalNotes());
//        System.out.println(notes.size());
    }

    private static String getYAML(EnNote note) throws JsonProcessingException {
        YAMLFactory yamlFactory = new YAMLFactory();
        yamlFactory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        yamlFactory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        ObjectMapper mapper = new ObjectMapper(yamlFactory);
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z EEE", Locale.US));
        mapper.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
//        NoteInfo note = mapper.readValue(new File("/home/shiwen/nt"), NoteInfo.class);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(note);
//        System.out.println(note.reference == null);
    }
}
