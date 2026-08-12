package com.novax.leadora.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "quotation")
@Getter
@Setter
public class QuotationProperties {
    private int otpExpirySeconds = 900;
    private String portalBaseUrl;
}
