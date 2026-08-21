package com.shipspace.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MongoIndexInitializerTest {

    @Test
    void indexedCollections_useActualDocumentCollectionNames() {
        assertThat(MongoIndexInitializer.INDEXED_COLLECTIONS)
                .contains("following", "score_event_log")
                .doesNotContain("followings", "score_event_logs");
    }

    @Test
    void indexedCollections_includeReportsForModerationQueries() {
        assertThat(MongoIndexInitializer.INDEXED_COLLECTIONS)
                .contains("reports");
        assertThat(MongoIndexInitializer.REPORT_STATUS_CREATED_AT_INDEX)
                .isEqualTo("status_createdAt_idx");
    }

    @Test
    void auditIndexes_supportAdminAuditLogBrowsing() {
        assertThat(MongoIndexInitializer.AUDIT_TIMESTAMP_INDEX)
                .isEqualTo("audit_timestamp_desc");
        assertThat(MongoIndexInitializer.AUDIT_EVENT_TYPE_TIMESTAMP_INDEX)
                .isEqualTo("eventType_timestamp_idx");
    }
}
