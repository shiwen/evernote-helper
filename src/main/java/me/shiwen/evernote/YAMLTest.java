package me.shiwen.evernote;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class YAMLTest {

    public static void main(String... args) throws IOException {
        YAMLFactory yamlFactory = new YAMLFactory();
        yamlFactory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        yamlFactory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        ObjectMapper mapper = new ObjectMapper(yamlFactory);
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z EEE"));
        mapper.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        NoteInfo note = mapper.readValue(new File("/home/shiwen/nt"), NoteInfo.class);
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(note));
        System.out.println(note.reference == null);
    }
}

@JsonPropertyOrder({"title", "created", "updated", "author", "reference", "notebook", "tags", "content"})
class NoteInfo {
    @JsonProperty("title")
    String title;
    @JsonProperty("created")
    Date created;
    @JsonProperty("updated")
    Date updated;
    @JsonProperty("author")
    String author;
    @JsonProperty("reference")
    String reference;
    @JsonProperty("notebook")
    String notebook;
    @JsonProperty("tags")
    String[] tags;
    @JsonProperty("content")
    String content;
}
