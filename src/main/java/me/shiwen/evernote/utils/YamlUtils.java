package me.shiwen.evernote.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import me.shiwen.evernote.model.LocalNote;

import java.io.IOException;
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
}
