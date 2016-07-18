package me.shiwen.evernote.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Date;

@JsonPropertyOrder({"title", "created", "updated", "version", "hash", "reference", "notebook", "tags", "content"})
public class LocalNote {
    @JsonProperty("title")
    public String title;
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
    @JsonProperty("notebook")
    public String notebook;
    @JsonProperty("tags")
    public String[] tags;
    @JsonProperty("content")
    public String content;
}
