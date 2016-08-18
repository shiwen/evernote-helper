package me.shiwen.evernote.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Date;

@JsonPropertyOrder({"title", "notebook", "created", "updated", "version", "hash", "reference", "tags", "content"})
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
}
