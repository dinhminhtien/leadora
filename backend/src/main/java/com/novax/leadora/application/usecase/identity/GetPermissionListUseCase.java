package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.api.dto.response.PermissionResponse;
import com.novax.leadora.infrastructure.persistence.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** UC-6.4 — the full permission catalogue the Admin assigns from. */
@Service
@RequiredArgsConstructor
public class GetPermissionListUseCase {

    private final PermissionRepository permissionRepository;

    @Transactional(readOnly = true)
    public List<PermissionResponse> execute() {
        return permissionRepository.findAllByOrderByPermissionIdAsc()
                .stream()
                .map(PermissionResponse::from)
                .toList();
    }
}
