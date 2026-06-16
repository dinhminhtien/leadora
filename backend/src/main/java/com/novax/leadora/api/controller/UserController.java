package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.response.UserSummaryResponse;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class UserController {

    private final UserRepository userRepository;

    /** Get all active users — used for assignee dropdowns */
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> getUsers() {
        List<UserSummaryResponse> users = userRepository.findAllWithRole()
                .stream()
                .map(UserSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(users));
    }
}
