package com.novax.leadora.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private UserInfo user;
    private String accessToken;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private String id;
        private String email;
        private String name;
        private List<String> roles;
        /** Effective permission codes (e.g. LEAD_VIEW) — drives frontend sidebar/route gating. */
        private List<String> permissions;
    }
}
