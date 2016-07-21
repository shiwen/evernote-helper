package me.shiwen.evernote.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import me.shiwen.evernote.model.LocalNote;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.MINIMIZE_QUOTES;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.WRITE_DOC_START_MARKER;

public class YamlUtils {
    private static final YAMLFactory YAML_FACTORY = new YAMLFactory();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z EEE", Locale.US);
    private static final TimeZone TIME_ZONE = TimeZone.getTimeZone("Asia/Shanghai");

    static {
        YAML_FACTORY.enable(MINIMIZE_QUOTES);
        YAML_FACTORY.disable(WRITE_DOC_START_MARKER);
    }

    private static ObjectMapper getMapper() {
        ObjectMapper mapper = new ObjectMapper(YAML_FACTORY);
        mapper.setDateFormat(DATE_FORMAT);
        mapper.setTimeZone(TIME_ZONE);
        return mapper;
    }

    public static String dump(LocalNote localNote) throws JsonProcessingException {
        return getMapper().writerWithDefaultPrettyPrinter().writeValueAsString(localNote);
    }

    public static LocalNote load(String yaml) throws IOException {
        return getMapper().readValue(yaml, LocalNote.class);
    }

    public static void main(String... args) throws IOException {
//        String yaml1 = new String(Files.readAllBytes(Paths.get("notes2", "0ad9ae6c-c40d-47cb-b3fd-ccb4155ea6ae")));
//        LocalNote note1 = load(yaml1);
////        System.out.println(note.content);
//        note1.content = note1.content.replace("public ", "public");
////        System.out.println(note.content);
//        System.out.println(dump(clone(note1)));
//        Files.write(Paths.get("d1"), note1.content.getBytes());
////        Files.write(Paths.get("d"), dump(clone(note)).getBytes());
////        String yaml2 = new String(Files.readAllBytes(Paths.get("d")));
////        LocalNote note2 = load(yaml2);
////        System.out.println(note2.content);
////        System.out.println(dump(clone(note2)));
////        Files.write(Paths.get("d2"), note2.content.getBytes());
        for (String guid : files) {
            String yaml = new String(Files.readAllBytes(Paths.get("notes2", guid)));
            LocalNote note = load(yaml);
            note.content = cleanse(note.content);
            Files.write(Paths.get("nd", guid), dump(note).getBytes());
        }
    }

    public static String cleanse(String s) {
        return s.replaceAll("\\s+\n", "\n").replace("\t", "    ").replace("\ufeff", "");
    }

    public static LocalNote clone(LocalNote n) {
        LocalNote n1 = new LocalNote();
        n1.content = n.content;
        return n1;
    }

    public static final String[] files = new String[] {
            "2130d2a2-9a18-4aae-8841-e22ab6c36686",
            "f7e3ef54-b886-4644-909e-efa02a15254a"
    };
}
