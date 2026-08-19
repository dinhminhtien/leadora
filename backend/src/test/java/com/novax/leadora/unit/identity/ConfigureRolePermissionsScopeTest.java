package com.novax.leadora.unit.identity;

import com.novax.leadora.api.dto.request.UpdateRolePermissionsRequest;
import com.novax.leadora.api.dto.response.RoleResponse;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.application.usecase.identity.ConfigureRolePermissionsUseCase;
import com.novax.leadora.application.usecase.identity.PermissionDependencyResolver;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.PermissionEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.RolePermissionEntity;
import com.novax.leadora.infrastructure.persistence.repository.PermissionRepository;
import com.novax.leadora.infrastructure.persistence.repository.RolePermissionRepository;
import com.novax.leadora.infrastructure.persistence.repository.RoleRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The scope rule has to hold at the API, not only in the grid.
 *
 * <p>Hiding a toggle stops an Admin clicking it; it does nothing about a stale browser tab, a
 * replayed request, or anyone calling the endpoint directly. If only the UI enforced this, the
 * database would keep collecting grants no endpoint honours — which is the state these tests
 * describe cleaning up.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConfigureRolePermissionsScopeTest {

    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private SystemAuditLogService systemAuditLogService;
    @Mock private CurrentUserProvider currentUserProvider;

    private ConfigureRolePermissionsUseCase useCase;

    /** The handful of codes these tests exercise, with the ids the fake repository hands back. */
    private static final Map<String, Integer> CODES = Map.of(
            "HANDOVER_VIEW", 1,
            "HANDOVER_WRITE", 2,
            "NOTIFICATION_VIEW", 3,
            "CHAT_VIEW", 4,
            "PAYMENT_VIEW", 5,
            "PAYMENT_WRITE", 6,
            "LEAD_VIEW", 7,
            // The out-of-scope pair these tests prune. It used to be PAYMENT_*, which stopped
            // working as an example once the arrival desk was given the payment settlement it had
            // always had everywhere except RolePermissionScope. Reservation status is a genuine
            // Reservation-desk surface with no Front Office route.
            "RESERVATION_VIEW", 8,
            "RESERVATION_WRITE", 9
    );

    private List<PermissionEntity> catalogue;

    @BeforeEach
    void setUp() {
        catalogue = CODES.entrySet().stream()
                .map(e -> {
                    PermissionEntity p = new PermissionEntity();
                    p.setPermissionId(e.getValue());
                    p.setPermissionCode(e.getKey());
                    p.setModule(e.getKey().substring(0, e.getKey().lastIndexOf('_')));
                    p.setAction(e.getKey().endsWith("_VIEW") ? "VIEW" : "WRITE");
                    return p;
                })
                .collect(Collectors.toCollection(ArrayList::new));
        // WRITE depends on its module's VIEW, exactly as the seed sets depends_on_id.
        catalogue.forEach(p -> {
            if ("WRITE".equals(p.getAction())) {
                catalogue.stream()
                        .filter(v -> v.getModule().equals(p.getModule()) && "VIEW".equals(v.getAction()))
                        .findFirst()
                        .ifPresent(v -> p.setDependsOnId(v.getPermissionId()));
            }
        });

        when(permissionRepository.findAll()).thenReturn(catalogue);
        when(permissionRepository.findAllByOrderByPermissionIdAsc()).thenReturn(catalogue);
        when(permissionRepository.existsById(anyInt())).thenAnswer(
                i -> catalogue.stream().anyMatch(p -> p.getPermissionId().equals(i.getArgument(0))));
        when(permissionRepository.findById(anyInt())).thenAnswer(
                i -> catalogue.stream().filter(p -> p.getPermissionId().equals(i.getArgument(0))).findFirst());
        when(permissionRepository.getReferenceById(anyInt())).thenAnswer(
                i -> catalogue.stream().filter(p -> p.getPermissionId().equals(i.getArgument(0))).findFirst().orElseThrow());
        lenient().when(rolePermissionRepository.findByRole_RoleId(anyInt())).thenReturn(List.of());
        lenient().when(userRepository.countByRole_RoleId(anyInt())).thenReturn(3L);

        useCase = new ConfigureRolePermissionsUseCase(
                roleRepository, permissionRepository, rolePermissionRepository, userRepository,
                new PermissionDependencyResolver(permissionRepository),
                systemAuditLogService, currentUserProvider);
    }

    private void givenRole(String roleName) {
        RoleEntity role = new RoleEntity();
        role.setRoleId(9);
        role.setRoleName(roleName);
        when(roleRepository.findById(9)).thenReturn(Optional.of(role));
    }

    private Set<String> savedCodes() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RolePermissionEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(rolePermissionRepository).saveAll(captor.capture());
        return captor.getValue().stream()
                .map(rp -> rp.getPermission().getPermissionCode())
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Front Office cannot be granted the AI assistant, however the request arrives")
    void dropsCodesTheRoleHasNoFunctionFor() {
        givenRole("FO");

        UpdateRolePermissionsRequest request = new UpdateRolePermissionsRequest();
        request.setPermissionIds(List.of(
                CODES.get("HANDOVER_VIEW"),
                CODES.get("CHAT_VIEW"),          // ChatController is SALES/MANAGER only
                CODES.get("RESERVATION_VIEW"),   // no Front Office reservation-status screen
                CODES.get("LEAD_VIEW")));        // not remotely this desk's job

        RoleResponse response = useCase.execute(9, request);

        assertThat(savedCodes()).containsExactly("HANDOVER_VIEW");
        assertThat(response.getPermissions())
                .extracting(p -> p.getPermissionCode())
                .containsExactly("HANDOVER_VIEW");
    }

    @Test
    @DisplayName("A write survives only when its own view is inside the scope too")
    void keepsTheDependencyChainIntactAfterPruning() {
        givenRole("FO");

        UpdateRolePermissionsRequest request = new UpdateRolePermissionsRequest();
        // RESERVATION_WRITE is out of scope and so is its RESERVATION_VIEW prerequisite: neither
        // may survive, and the handover pair must not be collateral damage.
        request.setPermissionIds(List.of(
                CODES.get("HANDOVER_VIEW"), CODES.get("HANDOVER_WRITE"),
                CODES.get("RESERVATION_VIEW"), CODES.get("RESERVATION_WRITE")));

        useCase.execute(9, request);

        assertThat(savedCodes()).containsExactlyInAnyOrder("HANDOVER_VIEW", "HANDOVER_WRITE");
    }

    @Test
    @DisplayName("A request made entirely of out-of-scope codes strips the role rather than failing")
    void anEntirelyOutOfScopeRequestSavesNothing() {
        givenRole("FO");

        UpdateRolePermissionsRequest request = new UpdateRolePermissionsRequest();
        request.setPermissionIds(List.of(CODES.get("CHAT_VIEW"), CODES.get("LEAD_VIEW")));

        RoleResponse response = useCase.execute(9, request);

        verify(rolePermissionRepository, never()).saveAll(any());
        assertThat(response.getPermissions()).isEmpty();
    }

    @Test
    @DisplayName("Sales keeps what it can actually use")
    void leavesInScopeRequestsAlone() {
        givenRole("SALES");

        UpdateRolePermissionsRequest request = new UpdateRolePermissionsRequest();
        request.setPermissionIds(List.of(
                CODES.get("LEAD_VIEW"), CODES.get("CHAT_VIEW"),
                CODES.get("PAYMENT_VIEW"), CODES.get("PAYMENT_WRITE")));

        useCase.execute(9, request);

        assertThat(savedCodes())
                .containsExactlyInAnyOrder("LEAD_VIEW", "CHAT_VIEW", "PAYMENT_VIEW", "PAYMENT_WRITE");
    }
}
