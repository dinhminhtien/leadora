package com.novax.leadora.unit.contract;

import com.novax.leadora.config.ContractProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The backend reads {@code ../.env} as a properties file, where quotes are part of the value rather
 * than syntax. {@code .env} declares {@code SUPABASE_SERVICE_ROLE_KEY} twice and the later, quoted
 * declaration wins, so the key reached {@code SupabaseStorageAdapter} wrapped in quotes and went out
 * as {@code Authorization: Bearer "eyJ..."}. Supabase Storage answered
 * {@code JWS Protected Header is invalid}, which surfaced as a 500 {@code PDF_UPLOAD_FAILED} when
 * sending a contract.
 */
class ContractStoragePropertiesTest {

    @Test
    @DisplayName("a quoted service role key is unwrapped before it reaches the Authorization header")
    void stripsQuotesFromServiceRoleKey() {
        ContractProperties.Storage storage = new ContractProperties.Storage();

        storage.setServiceRoleKey("\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.signature\"");

        assertThat(storage.getServiceRoleKey())
                .isEqualTo("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.signature");
    }

    @Test
    @DisplayName("an unquoted key is left exactly as it is")
    void leavesBareKeyUntouched() {
        ContractProperties.Storage storage = new ContractProperties.Storage();

        storage.setServiceRoleKey("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.signature");

        assertThat(storage.getServiceRoleKey())
                .isEqualTo("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.signature");
    }

    @Test
    @DisplayName("the storage URL is unwrapped too — .env quotes the Supabase URL in places")
    void stripsQuotesFromSupabaseUrl() {
        ContractProperties.Storage storage = new ContractProperties.Storage();

        storage.setSupabaseUrl("\"https://project.supabase.co\"");

        assertThat(storage.getSupabaseUrl()).isEqualTo("https://project.supabase.co");
    }

    @Test
    @DisplayName("a stray quote on one side is not treated as a wrapper")
    void keepsUnbalancedQuotes() {
        ContractProperties.Storage storage = new ContractProperties.Storage();

        storage.setServiceRoleKey("\"eyJhbGciOi");

        assertThat(storage.getServiceRoleKey()).isEqualTo("\"eyJhbGciOi");
    }

    @Test
    @DisplayName("an absent key stays null rather than becoming a literal")
    void tolerariesNull() {
        ContractProperties.Storage storage = new ContractProperties.Storage();

        storage.setServiceRoleKey(null);

        assertThat(storage.getServiceRoleKey()).isNull();
    }
}
