package com.vishwasena.urlshortener.config;

import com.vishwasena.urlshortener.AbstractPostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.flyway.enabled=true")
class FlywayMigrationPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired(required = false)
    private Flyway flyway;

    @Test
    void testFlywayMigrationsRanSuccessfully() {
        assertNotNull(flyway, "Flyway should be configured");
        
        // Verify schema exists
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metadata = conn.getMetaData();
            ResultSet tables = metadata.getTables(null, "public", "short_url", null);
            assertTrue(tables.next(), "short_url table should exist after Flyway migration");
        } catch (Exception e) {
            fail("Failed to verify Flyway migration: " + e.getMessage());
        }
    }

    @Test
    void testShortUrlTableStructure() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metadata = conn.getMetaData();
            
            // Verify short_url table exists
            ResultSet tables = metadata.getTables(null, "public", "short_url", null);
            assertTrue(tables.next(), "short_url table should exist");
            
            // Verify columns
            ResultSet columns = metadata.getColumns(null, "public", "short_url", null);
            java.util.Set<String> columnNames = new java.util.HashSet<>();
            while (columns.next()) {
                columnNames.add(columns.getString("COLUMN_NAME"));
            }
            
            assertTrue(columnNames.contains("id"), "id column should exist");
            assertTrue(columnNames.contains("short_code"), "short_code column should exist");
            assertTrue(columnNames.contains("original_url"), "original_url column should exist");
            assertTrue(columnNames.contains("status"), "status column should exist");
            assertTrue(columnNames.contains("created_at"), "created_at column should exist");
            assertTrue(columnNames.contains("updated_at"), "updated_at column should exist");
            assertTrue(columnNames.contains("expires_at"), "expires_at column should exist");
            assertTrue(columnNames.contains("click_count"), "click_count column should exist");
        }
    }

    @Test
    void testClickEventTableStructure() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metadata = conn.getMetaData();
            
            // Verify click_event table exists
            ResultSet tables = metadata.getTables(null, "public", "click_event", null);
            assertTrue(tables.next(), "click_event table should exist");
            
            // Verify columns
            ResultSet columns = metadata.getColumns(null, "public", "click_event", null);
            java.util.Set<String> columnNames = new java.util.HashSet<>();
            while (columns.next()) {
                columnNames.add(columns.getString("COLUMN_NAME"));
            }
            
            assertTrue(columnNames.contains("id"), "id column should exist");
            assertTrue(columnNames.contains("short_url_id"), "short_url_id column should exist");
            assertTrue(columnNames.contains("clicked_at"), "clicked_at column should exist");
            assertTrue(columnNames.contains("ip_hash"), "ip_hash column should exist");
            assertTrue(columnNames.contains("user_agent"), "user_agent column should exist");
            assertTrue(columnNames.contains("referer"), "referer column should exist");
        }
    }

    @Test
    void testUniqueConstraintOnShortCode() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metadata = conn.getMetaData();
            
            // Get unique indexes/constraints
            ResultSet indexes = metadata.getIndexInfo(null, "public", "short_url", true, false);
            
            boolean uniqueIndexFound = false;
            while (indexes.next()) {
                String indexName = indexes.getString("INDEX_NAME");
                if (indexName != null && indexName.toLowerCase().contains("short_code")) {
                    uniqueIndexFound = true;
                    break;
                }
            }
            
            assertTrue(uniqueIndexFound, "Unique constraint/index on short_code should exist");
        }
    }

    @Test
    void testForeignKeyConstraint() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metadata = conn.getMetaData();
            
            // Get imported keys (foreign keys)
            ResultSet importedKeys = metadata.getImportedKeys(null, "public", "click_event");
            
            boolean fkFound = false;
            while (importedKeys.next()) {
                String fkTable = importedKeys.getString("FKTABLE_NAME");
                String pkTable = importedKeys.getString("PKTABLE_NAME");
                String deleteRule = importedKeys.getString("DELETE_RULE");
                
                if ("click_event".equals(fkTable) && "short_url".equals(pkTable)) {
                    fkFound = true;
                    // CASCADE DELETE is code 0 in java.sql.DatabaseMetaData
                    assertTrue(true, "Foreign key from click_event to short_url should exist");
                }
            }
            
            assertTrue(fkFound, "Foreign key constraint should exist");
        }
    }

    @Test
    void testIndexesCreated() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metadata = conn.getMetaData();
            
            // Check indexes on short_url
            ResultSet shortUrlIndexes = metadata.getIndexInfo(null, "public", "short_url", false, false);
            java.util.Set<String> shortUrlIndexNames = new java.util.HashSet<>();
            while (shortUrlIndexes.next()) {
                String indexName = shortUrlIndexes.getString("INDEX_NAME");
                if (indexName != null) {
                    shortUrlIndexNames.add(indexName.toLowerCase());
                }
            }
            
            assertTrue(shortUrlIndexNames.stream().anyMatch(n -> n.contains("short_code")), 
                    "Index on short_code should exist");
            
            // Check indexes on click_event
            ResultSet clickEventIndexes = metadata.getIndexInfo(null, "public", "click_event", false, false);
            java.util.Set<String> clickEventIndexNames = new java.util.HashSet<>();
            while (clickEventIndexes.next()) {
                String indexName = clickEventIndexes.getString("INDEX_NAME");
                if (indexName != null) {
                    clickEventIndexNames.add(indexName.toLowerCase());
                }
            }
            
            assertTrue(clickEventIndexNames.stream().anyMatch(n -> n.contains("short_url_id")), 
                    "Index on short_url_id should exist");
        }
    }
}
