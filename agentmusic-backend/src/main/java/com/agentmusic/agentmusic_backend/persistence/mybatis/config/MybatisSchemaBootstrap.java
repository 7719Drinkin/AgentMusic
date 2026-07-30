package com.agentmusic.agentmusic_backend.persistence.mybatis.config;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "agentmusic.persistence.mode", havingValue = "mybatis")
public class MybatisSchemaBootstrap {

    private static final Logger log = LoggerFactory.getLogger(MybatisSchemaBootstrap.class);
    private static final String SESSION_CONTEXT_MIGRATION = "20260425_add_session_context_columns.sql";
    private static final String BASE_SCHEMA_RESOURCE = "db/mysql/schema.sql";
    private static final String MIGRATION_RESOURCE_PATTERN = "classpath*:db/mysql/migrations/*.sql";
    private static final Set<String> REQUIRED_TABLES = Set.of(
            "users",
            "playlists",
            "playlist_tracks",
            "tracks",
            "artists",
            "chat_messages",
            "sessions",
            "schema_migrations"
    );

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final PathMatchingResourcePatternResolver resourceResolver;

    public MybatisSchemaBootstrap(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.resourceResolver = new PathMatchingResourcePatternResolver();
    }

    @PostConstruct
    void initialize() {
        String databaseDescription = describeDatabaseTarget();
        log.info("Initializing mybatis persistence bootstrap for {}", databaseDescription);
        runBootstrapStage(
                "base schema application",
                databaseDescription,
                () -> applyBaseSchema(databaseDescription),
                "Verify MYSQL_URL / MYSQL_USERNAME / MYSQL_PASSWORD and confirm " + BASE_SCHEMA_RESOURCE + " is present."
        );
        runBootstrapStage(
                "migration table initialization",
                databaseDescription,
                this::ensureMigrationTable,
                "Verify the database user can create and update schema_migrations."
        );
        runBootstrapStage(
                "migration application",
                databaseDescription,
                () -> applyPendingMigrations(databaseDescription),
                "Inspect db/mysql/migrations/*.sql and schema_migrations for partially applied changes."
        );
        runBootstrapStage(
                "schema validation",
                databaseDescription,
                this::validateSchema,
                "Check db/mysql/schema.sql, db/mysql/migrations, and the live database schema."
        );
    }

    private void applyBaseSchema(String databaseDescription) {
        Resource schema = new ClassPathResource(BASE_SCHEMA_RESOURCE);
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(false, false, "UTF-8", schema);
        populator.execute(dataSource);
        log.info("Ensured base MySQL schema from {} for {}", BASE_SCHEMA_RESOURCE, databaseDescription);
    }

    private void ensureMigrationTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    migration_name VARCHAR(255) PRIMARY KEY,
                    applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private void applyPendingMigrations(String databaseDescription) {
        Set<String> appliedMigrations = new HashSet<>(jdbcTemplate.query(
                "SELECT migration_name FROM schema_migrations",
                (resultSet, rowNum) -> resultSet.getString(1)
        ));
        int appliedCount = 0;
        int recordedSatisfiedCount = 0;

        for (Resource resource : loadMigrationResources()) {
            String migrationName = resource.getFilename();
            if (migrationName == null) {
                continue;
            }
            if (appliedMigrations.contains(migrationName)) {
                continue;
            }
            if (isAlreadySatisfied(migrationName)) {
                recordMigration(migrationName);
                recordedSatisfiedCount++;
                log.info("Recorded already satisfied MySQL migration {} for {}", migrationName, databaseDescription);
                continue;
            }

            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(false, false, "UTF-8", resource);
            try {
                populator.execute(dataSource);
                recordMigration(migrationName);
                appliedCount++;
                log.info("Applied MySQL migration {} for {}", migrationName, databaseDescription);
            } catch (RuntimeException exception) {
                throw new IllegalStateException(
                        "Failed while applying MySQL migration " + migrationName
                                + ". Check db/mysql/migrations and schema_migrations for partial state.",
                        exception
                );
            }
        }
        log.info(
                "MySQL migration scan completed for {}. Applied={}, alreadySatisfied={}",
                databaseDescription,
                appliedCount,
                recordedSatisfiedCount
        );
    }

    private List<Resource> loadMigrationResources() {
        try {
            Resource[] resources = resourceResolver.getResources(MIGRATION_RESOURCE_PATTERN);
            List<Resource> result = new ArrayList<>(List.of(resources));
            result.sort(Comparator.comparing(resource -> resource.getFilename() == null ? "" : resource.getFilename()));
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to load MySQL migration resources from " + MIGRATION_RESOURCE_PATTERN,
                    exception
            );
        }
    }

    private boolean isAlreadySatisfied(String migrationName) {
        if (SESSION_CONTEXT_MIGRATION.equals(migrationName)) {
            return hasColumn("sessions", "current_playlist_id") && hasColumn("sessions", "current_track_index");
        }
        return false;
    }

    private void recordMigration(String migrationName) {
        jdbcTemplate.update(
                """
                INSERT INTO schema_migrations (migration_name, applied_at)
                VALUES (?, NOW())
                ON DUPLICATE KEY UPDATE applied_at = VALUES(applied_at)
                """,
                migrationName
        );
    }

    private void validateSchema() {
        for (String tableName : REQUIRED_TABLES) {
            if (!hasTable(tableName)) {
                throw new IllegalStateException("Missing required MySQL table in mybatis mode: " + tableName);
            }
        }
        assertColumn("sessions", "current_playlist_id");
        assertColumn("sessions", "current_track_index");
        assertColumn("users", "preferences");
        log.info("Validated required MySQL schema for mybatis persistence mode");
    }

    private void assertColumn(String tableName, String columnName) {
        if (!hasColumn(tableName, columnName)) {
            throw new IllegalStateException(
                    "Missing required MySQL column " + tableName + "." + columnName
                            + ". Check db/mysql/schema.sql and db/mysql/migrations."
            );
        }
    }

    private boolean hasTable(String tableName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet resultSet = metaData.getTables(connection.getCatalog(), null, tableName, new String[]{"TABLE"})) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to inspect MySQL table metadata for " + tableName
                            + ". Check database connectivity and metadata permissions.",
                    exception
            );
        }
    }

    private boolean hasColumn(String tableName, String columnName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), null, tableName, columnName)) {
                if (resultSet.next()) {
                    return true;
                }
            }
            try (ResultSet resultSet = metaData.getColumns(
                    connection.getCatalog(),
                    null,
                    tableName.toUpperCase(Locale.ROOT),
                    columnName.toUpperCase(Locale.ROOT)
            )) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to inspect MySQL column metadata for " + tableName + "." + columnName,
                    exception
            );
        }
    }

    private void runBootstrapStage(
            String stageName,
            String databaseDescription,
            Runnable action,
            String remediation
    ) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    formatBootstrapFailureMessage(stageName, databaseDescription, remediation, exception.getMessage()),
                    exception
            );
        }
    }

    public static String formatBootstrapFailureMessage(
            String stageName,
            String databaseDescription,
            String remediation,
            String causeMessage
    ) {
        StringBuilder builder = new StringBuilder()
                .append("Mybatis persistence bootstrap failed during ")
                .append(stageName)
                .append(". Target=")
                .append(databaseDescription)
                .append(". ")
                .append(remediation);
        if (causeMessage != null && !causeMessage.isBlank()) {
            builder.append(" Cause: ").append(causeMessage);
        }
        return builder.toString();
    }

    private String describeDatabaseTarget() {
        try (Connection connection = dataSource.getConnection()) {
            String catalog = connection.getCatalog();
            String url = connection.getMetaData().getURL();
            if (catalog != null && !catalog.isBlank()) {
                return catalog + " @ " + url;
            }
            return url;
        } catch (SQLException exception) {
            log.warn("Failed to describe MySQL connection target for bootstrap diagnostics", exception);
            return "unresolved-mysql-target";
        }
    }
}
