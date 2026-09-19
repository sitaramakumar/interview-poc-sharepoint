package com.interview.poc.acl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.poc.config.AppConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resource-level ACL store, one entry per document id.
 *
 * LOCAL POC MODE:
 *   Reads/writes a documentId -> DocumentAcl map to a single JSON file on disk
 *   at data/acl/document-acl.json. A document with no entry here has never been
 *   ACL-tagged by this app (see DocumentAccessGuard for what that means).
 *
 * PRODUCTION MIGRATION:
 *   Either a real table (documentId, ownerId, allowedRoles) behind the same
 *   find/save/delete interface, or — since the documents themselves already
 *   live in SharePoint — the item-level permissions already exposed by Graph's
 *   /sites/{id}/drive/items/{id}/permissions endpoint, so access control isn't
 *   duplicated in a second system that can drift out of sync with SharePoint's
 *   own sharing settings.
 */
public class DocumentAclStore {
    private static final Path STORE_FILE = Paths.get(
            AppConfig.get("acl.local.path", "data/acl/document-acl.json"));
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, DocumentAcl>> MAP_TYPE = new TypeReference<>() {};

    public DocumentAclStore() {
        try {
            Files.createDirectories(STORE_FILE.getParent());
            if (Files.notExists(STORE_FILE)) {
                Files.writeString(STORE_FILE, MAPPER.writeValueAsString(new HashMap<>()));
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot initialize local ACL store at " + STORE_FILE, e);
        }
    }

    public Optional<DocumentAcl> find(String documentId) {
        return Optional.ofNullable(readAll().get(documentId));
    }

    public void save(String documentId, DocumentAcl acl) {
        Map<String, DocumentAcl> all = readAll();
        all.put(documentId, acl);
        writeAll(all);
    }

    public void delete(String documentId) {
        Map<String, DocumentAcl> all = readAll();
        if (all.remove(documentId) != null) {
            writeAll(all);
        }
    }

    private Map<String, DocumentAcl> readAll() {
        try {
            String content = Files.readString(STORE_FILE);
            return MAPPER.readValue(content, MAP_TYPE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read ACL store from " + STORE_FILE, e);
        }
    }

    private void writeAll(Map<String, DocumentAcl> all) {
        try {
            Files.writeString(STORE_FILE, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(all));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write ACL store to " + STORE_FILE, e);
        }
    }
}
