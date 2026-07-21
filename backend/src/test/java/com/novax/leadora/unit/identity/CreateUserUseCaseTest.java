package com.novax.leadora.unit.identity;

import com.novax.leadora.api.dto.request.CreateUserRequest;
import com.novax.leadora.api.dto.response.UserAccountResponse;
import com.novax.leadora.application.usecase.identity.CreateUserUseCase;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.RoleRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateUserUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CreateUserUseCase createUserUseCase;

    private CreateUserRequest buildValidRequest() {
        CreateUserRequest req = new CreateUserRequest();
        req.setFullName("Nguyen Van A");
        req.setEmail("nguyenvana@leadora.vn");
        req.setPassword("StrongPass1!");
        req.setPhone("0912345678");
        req.setRoleId(2);
        return req;
    }

    @Test
    @DisplayName("UT-CREATEUSER-01: Valid request → creates user with ACTIVE status")
    void testCreateUserSuccess() {
        CreateUserRequest request = buildValidRequest();
        RoleEntity staffRole = RoleEntity.builder().roleId(2).roleName("STAFF").build();

        when(userRepository.existsByEmailIgnoreCase("nguyenvana@leadora.vn")).thenReturn(false);
        when(roleRepository.findById(2)).thenReturn(Optional.of(staffRole));
        when(passwordEncoder.encode("StrongPass1!")).thenReturn("$2a$10$encoded");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity u = inv.getArgument(0);
            u.setUserId(UUID.randomUUID());
            return u;
        });

        UserAccountResponse response = createUserUseCase.execute(request);

        assertNotNull(response);
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("UT-CREATEUSER-02: Duplicate email → throws IllegalStateException")
    void testDuplicateEmailThrows() {
        CreateUserRequest request = buildValidRequest();

        when(userRepository.existsByEmailIgnoreCase("nguyenvana@leadora.vn")).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> createUserUseCase.execute(request));
        assertEquals("Email already exists.", ex.getMessage());
    }

    @Test
    @DisplayName("UT-CREATEUSER-03: Invalid role ID → throws ResourceNotFoundException")
    void testInvalidRoleThrows() {
        CreateUserRequest request = buildValidRequest();
        request.setRoleId(999);

        when(userRepository.existsByEmailIgnoreCase("nguyenvana@leadora.vn")).thenReturn(false);
        when(roleRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> createUserUseCase.execute(request));
    }

    @Test
    @DisplayName("UT-CREATEUSER-04: Admin role → throws IllegalStateException")
    void testAdminRoleThrows() {
        CreateUserRequest request = buildValidRequest();
        RoleEntity adminRole = RoleEntity.builder().roleId(2).roleName("ADMIN").build();

        when(userRepository.existsByEmailIgnoreCase("nguyenvana@leadora.vn")).thenReturn(false);
        when(roleRepository.findById(2)).thenReturn(Optional.of(adminRole));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> createUserUseCase.execute(request));
        assertTrue(ex.getMessage().contains("Admin role"));
    }

    @Test
    @DisplayName("UT-CREATEUSER-05: Weak password (no symbol) → throws IllegalStateException")
    void testWeakPasswordThrows() {
        CreateUserRequest request = buildValidRequest();
        request.setPassword("WeakPass1");
        RoleEntity staffRole = RoleEntity.builder().roleId(2).roleName("STAFF").build();

        when(userRepository.existsByEmailIgnoreCase("nguyenvana@leadora.vn")).thenReturn(false);
        when(roleRepository.findById(2)).thenReturn(Optional.of(staffRole));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> createUserUseCase.execute(request));
        assertTrue(ex.getMessage().contains("Password must contain"));
    }
}
