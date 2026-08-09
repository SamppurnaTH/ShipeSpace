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
}
