package me.shiwen.evernote.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import me.shiwen.evernote.model.LocalNote;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.MINIMIZE_QUOTES;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.WRITE_DOC_START_MARKER;

public class YamlUtils {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z EEE", Locale.US);
    private static final TimeZone TIME_ZONE = TimeZone.getTimeZone("Asia/Shanghai");
    private static final ObjectWriter WRITER;
    private static final ObjectReader READER;

    static {
        YAMLFactory yamlFactory = new YAMLFactory();
        yamlFactory.enable(MINIMIZE_QUOTES);
        yamlFactory.disable(WRITE_DOC_START_MARKER);
        ObjectMapper objectMapper = new ObjectMapper(yamlFactory);
        objectMapper.setDateFormat(DATE_FORMAT);
        objectMapper.setTimeZone(TIME_ZONE);
        WRITER = objectMapper.writerFor(LocalNote.class);
        READER = objectMapper.readerFor(LocalNote.class);
    }

    public static String dump(LocalNote localNote) throws JsonProcessingException {
        return WRITER.writeValueAsString(localNote);
    }

    public static LocalNote load(String yaml) throws IOException {
        return READER.readValue(yaml);
    }

    public static void main(String... args) throws IOException {
        String s = "title: null\n" +
                "notebook: null\n" +
                "created: null\n" +
                "updated: null\n" +
                "version: 0\n" +
                "hash: null\n" +
                "reference: null\n" +
                "tags: null\n" +
                "content: test_content\n" +
                "line-wrap: true";
        LocalNote note = load(s);
        System.out.println(note.verbose);

//        LocalNote note = new LocalNote();
//        System.out.println(dump(note));
    }
}
