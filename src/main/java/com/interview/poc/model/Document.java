package com.interview.poc.model;

import java.util.Date;

public class Document {
    private String id;
    private String title;
    private String content;
    private String modifiedBy;
    private Date modifiedAt;

    public Document() {}

    public Document(String id, String title, String content, String modifiedBy) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.modifiedBy = modifiedBy;
        this.modifiedAt = new Date();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getModifiedBy() { return modifiedBy; }
    public void setModifiedBy(String modifiedBy) { this.modifiedBy = modifiedBy; }
    public Date getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(Date modifiedAt) { this.modifiedAt = modifiedAt; }
}
