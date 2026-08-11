package com.novax.leadora.infrastructure.integration.supabase;

import com.novax.leadora.config.ContractProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class SupabaseStorageAdapter {

    private final RestClient restClient;
    private final ContractProperties contractProperties;

    public SupabaseStorageAdapter(ContractProperties contractProperties) {
        this.contractProperties = contractProperties;
        
        String baseUrl = contractProperties.getStorage().getSupabaseUrl();
        if (baseUrl != null && baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl != null ? baseUrl : "")
                .defaultHeader("Authorization", "Bearer " + contractProperties.getStorage().getServiceRoleKey())
                .build();
    }

    /**
     * Uploads a contract PDF file to Supabase Storage.
     *
     * @param path    the relative path inside the bucket (e.g. "contractId/HD-2026-000001.pdf")
     * @param content the raw bytes of the PDF file
     * @return the public URL of the uploaded document
     */
    public String uploadPdf(String path, byte[] content) {
        String bucket = contractProperties.getStorage().getBucket();
        String uploadUri = "/storage/v1/object/" + bucket + "/" + path;
        
        log.info("Uploading PDF to Supabase Storage: {}/{}", bucket, path);
        
        try {
            // Upload the file. Supabase expects the raw bytes directly.
            // We use x-upsert: true header to allow overwriting if needed.
            this.restClient.post()
                    .uri(uploadUri)
                    .header("x-upsert", "true")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(content)
                    .retrieve()
                    .toBodilessEntity();
            
            // Build public URL
            String baseUrl = contractProperties.getStorage().getSupabaseUrl();
            if (baseUrl != null && baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            
            String publicUrl = baseUrl + "/storage/v1/object/public/" + bucket + "/" + path;
            log.info("PDF successfully uploaded to: {}", publicUrl);
            return publicUrl;
        } catch (Exception e) {
            log.error("Failed to upload PDF to Supabase Storage: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload contract PDF: " + e.getMessage(), e);
        }
    }
}
