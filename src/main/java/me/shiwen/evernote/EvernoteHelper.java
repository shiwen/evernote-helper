package me.shiwen.evernote;

import com.evernote.auth.EvernoteAuth;
import com.evernote.auth.EvernoteService;
import com.evernote.clients.ClientFactory;
import com.evernote.clients.NoteStoreClient;
import com.evernote.clients.UserStoreClient;
import com.evernote.edam.notestore.NoteFilter;
import com.evernote.edam.notestore.NoteList;
import com.evernote.edam.type.Note;
import com.evernote.edam.type.NoteSortOrder;
import com.evernote.edam.type.Notebook;

import java.util.List;

public class EvernoteHelper {
    private static final String TOKEN = "";

    public static void main(String... args) throws Exception {

        EvernoteAuth evernoteAuth = new EvernoteAuth(EvernoteService.YINXIANG, TOKEN);
        ClientFactory factory = new ClientFactory(evernoteAuth);
        UserStoreClient userStore = factory.createUserStoreClient();
        NoteStoreClient noteStore = factory.createNoteStoreClient();

        List<Notebook> notebooks = noteStore.listNotebooks();
        for (Notebook notebook : notebooks) {
            System.out.println("Notebook: " + notebook.getName());
        }
        NoteFilter filter = new NoteFilter();
        filter.setOrder(NoteSortOrder.CREATED.getValue());
        filter.setAscending(true);
        NoteList noteList = noteStore.findNotes(filter, 1000, 100);
        List<Note> notes = noteList.getNotes();
        for (Note note : notes) {
            System.out.println(" * " + note.getTitle());
            Note fullNote = noteStore.getNote(note.getGuid(), true, true, false, false);
            System.out.println(fullNote.getContent());
            System.out.println(fullNote.getAttributes());
        }
        System.out.println(noteList.getTotalNotes());
        System.out.println(notes.size());
    }
}
