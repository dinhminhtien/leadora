package com.novax.leadora.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "contract")
@Getter
@Setter
public class ContractProperties {
    private int otpExpirySeconds = 900;
    private final Storage storage = new Storage();
    private String portalBaseUrl;

    @Getter
    @Setter
    public static class Storage {
        private String bucket = "contract_files";
        private String supabaseUrl;
        private String serviceRoleKey;

        public void setSupabaseUrl(String supabaseUrl) {
            this.supabaseUrl = unquote(supabaseUrl);
        }

        public void setServiceRoleKey(String serviceRoleKey) {
            this.serviceRoleKey = unquote(serviceRoleKey);
        }

        /**
         * Strips the double quotes an {@code .env} value may be written with.
         *
         * <p>The backend reads {@code ../.env} as a <b>properties</b> file, and properties files do
         * not treat quotes as syntax — they are part of the value. {@code .env} currently declares
         * {@code SUPABASE_SERVICE_ROLE_KEY} twice, and the later (quoted) declaration is the one that
         * wins, so the key arrived here wrapped in quotes and went out as
         * {@code Authorization: Bearer "eyJ..."}. Supabase Storage rejected that with
         * {@code JWS Protected Header is invalid}, which surfaced as a 500
         * {@code PDF_UPLOAD_FAILED} on sending a contract. {@code WebSecurityConfig#jwtDecoder} and
         * {@code JwtService} already strip quotes for the same reason; this keeps the storage
         * credentials working whichever way the value is written.
         */
        private static String unquote(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            if (trimmed.length() > 1 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                return trimmed.substring(1, trimmed.length() - 1);
            }
            return trimmed;
        }
    }
}
