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
    }
}
