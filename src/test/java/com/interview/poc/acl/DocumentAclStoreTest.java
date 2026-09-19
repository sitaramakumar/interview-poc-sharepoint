package com.interview.poc.acl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each test uses a fresh random document id so they don't collide with each
 * other, with real app data, or across runs — DocumentAclStore keeps every
 * document's ACL in one shared JSON file (data/acl/document-acl.json by
 * default), same pattern as VaultService's single-file local store.
 */
class DocumentAclStoreTest {

    private final DocumentAclStore store = new DocumentAclStore();

    @Test
    void findReturnsEmptyForADocumentThatWasNeverSaved() {
        assertTrue(store.find("doc-" + UUID.randomUUID()).isEmpty());
    }

    @Test
    void saveThenFindRoundTripsOwnerAndAllowedRoles() {
        String id = "doc-" + UUID.randomUUID();
        store.save(id, new DocumentAcl("owner-1", List.of("WRITE", "ADMIN")));

        DocumentAcl found = store.find(id).orElseThrow();
        assertEquals("owner-1", found.getOwnerId());
        assertEquals(List.of("WRITE", "ADMIN"), found.getAllowedRoles());
    }

    @Test
    void saveOverwritesAnExistingEntryForTheSameId() {
        String id = "doc-" + UUID.randomUUID();
        store.save(id, new DocumentAcl("owner-1", List.of("READ")));
        store.save(id, new DocumentAcl("owner-2", List.of("ADMIN")));

        DocumentAcl found = store.find(id).orElseThrow();
        assertEquals("owner-2", found.getOwnerId());
        assertEquals(List.of("ADMIN"), found.getAllowedRoles());
    }

    @Test
    void deleteRemovesTheEntry() {
        String id = "doc-" + UUID.randomUUID();
        store.save(id, new DocumentAcl("owner-1", List.of("READ")));

        store.delete(id);

        assertTrue(store.find(id).isEmpty());
    }

    @Test
    void deleteOfAnUnknownIdIsANoOp() {
        store.delete("doc-" + UUID.randomUUID());
    }
}
