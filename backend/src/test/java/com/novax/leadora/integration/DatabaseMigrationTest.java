package com.novax.leadora.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
                "spring.ai.google.genai.vertex-ai=false",
                "spring.ai.google.genai.api-key=dummy-api-key",
                "spring.ai.google.genai.embedding.api-key=dummy-api-key",
                "spring.jpa.hibernate.ddl-auto=none"
})
@ActiveProfiles("dev")
class DatabaseMigrationTest {

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Test
        void runMigration() {
                System.out.println("Running Database Migration for Leadora CRM refinement...");

                // 1. Add total_rooms to product_services
                jdbcTemplate.execute("ALTER TABLE product_services ADD COLUMN IF NOT EXISTS total_rooms INTEGER;");
                System.out.println("Added total_rooms column to product_services successfully.");

                // 2. Add acceptance token fields to quotations
                jdbcTemplate.execute("ALTER TABLE quotations ADD COLUMN IF NOT EXISTS acceptance_token VARCHAR(100);");
                jdbcTemplate.execute(
                                "ALTER TABLE quotations ADD COLUMN IF NOT EXISTS token_expiry TIMESTAMP WITH TIME ZONE;");
                jdbcTemplate.execute(
                                "ALTER TABLE quotations ADD COLUMN IF NOT EXISTS token_used BOOLEAN DEFAULT FALSE;");
                System.out.println("Added acceptance token columns to quotations successfully.");

                // 3. Add unique index on acceptance_token
                jdbcTemplate.execute(
                                "CREATE UNIQUE INDEX IF NOT EXISTS idx_quotations_acceptance_token ON quotations(acceptance_token) WHERE acceptance_token IS NOT NULL;");
                System.out.println("Created unique index on acceptance_token successfully.");

                // 4. Create quotation_acceptance_logs table
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quotation_acceptance_logs (" +
                                "log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(), " +
                                "quotation_id UUID NOT NULL REFERENCES quotations(quotation_id) ON DELETE CASCADE, " +
                                "action VARCHAR(20) NOT NULL, " +
                                "logged_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(), " +
                                "ip_address VARCHAR(45), " +
                                "user_agent VARCHAR(500), " +
                                "customer_note TEXT" +
                                ");");
                System.out.println("Created quotation_acceptance_logs table successfully.");

                // 5. Initialize default capacity for existing room products (e.g. 10 rooms per
                // room type if null)
                jdbcTemplate.execute(
                                "UPDATE product_services SET total_rooms = 10 WHERE category = 'ROOM' AND total_rooms IS NULL;");
                System.out.println("Initialized total_rooms capacity for existing ROOM categories.");
        }
}
