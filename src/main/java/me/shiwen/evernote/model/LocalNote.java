package me.shiwen.evernote.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Arrays;
import java.util.Date;

@JsonPropertyOrder({"title", "notebook", "created", "updated", "version",
        "hash", "reference", "tags", "content", "verbose"})
public class LocalNote {
    @JsonProperty("title")
    public String title;
    @JsonProperty("notebook")
    public String notebook;
    @JsonProperty("created")
    public Date created;
    @JsonProperty("updated")
    public Date updated;
    @JsonProperty("version")
    public int version;
    @JsonProperty("hash")
    public String hash;
    @JsonProperty("reference")
    public String reference;
    @JsonProperty("tags")
    public String[] tags;
    @JsonProperty("content")
    public String content;
    @JsonProperty("verbose")
    @JsonInclude(Include.NON_DEFAULT)
    public boolean verbose;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LocalNote)) {
            return false;
        }

        if (this == o) {
            return true;
        }

        LocalNote note = (LocalNote) o;
        return this.title.equals(note.title) &&
                this.notebook.equals(note.notebook) &&
                this.created.equals(note.created) &&
                this.updated.equals(note.updated) &&
                this.version == note.version &&
                this.hash.equals(note.hash) &&
                this.reference.equals(note.reference) &&
                Arrays.equals(this.tags, note.tags) &&
                this.content.equals(note.content) &&
                this.verbose == note.verbose;
    }
}
