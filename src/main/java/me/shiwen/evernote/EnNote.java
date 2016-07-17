package me.shiwen.evernote;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Date;

@JsonPropertyOrder({"title", "created", "updated", "author", "reference", "notebook", "tags", "content"})
class EnNote {
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
