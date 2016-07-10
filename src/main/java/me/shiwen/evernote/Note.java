package me.shiwen.evernote;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Note {
    @JsonProperty("created_at")
    public String createdAt;
    @JsonProperty("modified_at")
    public String modifiedAt;
    public String content;

//    public String getCreatedAt() {
//        return createdAt;
//    }
//
//    public void setCreatedAt(String createdAt) {
//        this.createdAt = createdAt;
//    }
//
//    public String getModifiedAt() {
//        return modifiedAt;
//    }
//
//    public void setModifiedAt(String modifiedAt) {
//        this.modifiedAt = modifiedAt;
//    }
//
//    public String getContent() {
//        return content;
//    }
//
//    public void setContent(String content) {
//        this.content = content;
//    }
}
