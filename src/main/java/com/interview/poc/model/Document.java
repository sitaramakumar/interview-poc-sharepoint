package com.interview.poc.model;

import java.util.Date;
import java.util.List;

public class Document {
    private String id;
    private String title;
    private String content;
    private String modifiedBy;
    private Date modifiedAt;

    /**
     * Resource-level ACL, on top of the coarse role/method check in DocumentApi.
     * Populated from com.interview.poc.acl.DocumentAclStore when a document is
     * read; optionally supplied by the caller on upload to set who besides the
     * owner may access it. Not present at all for documents this app has never
     * ACL-tagged (see DocumentAccessGuard for the fail-open behavior on that).
     */
    private String ownerId;
    private List<String> allowedRoles;

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
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public List<String> getAllowedRoles() { return allowedRoles; }
    public void setAllowedRoles(List<String> allowedRoles) { this.allowedRoles = allowedRoles; }
}
