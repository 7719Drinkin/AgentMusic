package com.agentmusic.agentmusic_backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmusic.agentmusic_backend.persistence.mybatis.config.MybatisSchemaBootstrap;
import org.junit.jupiter.api.Test;

class MybatisSchemaBootstrapTests {

    @Test
    void formatBootstrapFailureMessageShouldIncludeStageTargetRemediationAndCause() {
        String message = MybatisSchemaBootstrap.formatBootstrapFailureMessage(
                "migration application",
                "agentmusic @ jdbc:mysql://localhost:3306/agentmusic",
                "Inspect db/mysql/migrations and schema_migrations for partially applied changes.",
                "Failed while applying MySQL migration 20260425_add_session_context_columns.sql"
        );

        assertThat(message).contains("migration application");
        assertThat(message).contains("agentmusic @ jdbc:mysql://localhost:3306/agentmusic");
        assertThat(message).contains("Inspect db/mysql/migrations and schema_migrations for partially applied changes.");
        assertThat(message).contains("Failed while applying MySQL migration 20260425_add_session_context_columns.sql");
    }
}
