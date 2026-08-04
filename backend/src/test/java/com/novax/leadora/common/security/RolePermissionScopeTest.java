package com.novax.leadora.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ties {@link RolePermissionScope} to the annotations it claims to mirror.
 *
 * <p>The scope sets are written by hand, and nothing in the compiler connects them to the
 * {@code @PreAuthorize} expressions that decide what a role can really do. Left unchecked they
 * drift the moment somebody narrows a controller — and the failure is invisible: the UC-6.4 grid
 * keeps offering a toggle whose permission the endpoint no longer honours, which is the exact class
 * of bug this scope was introduced to remove.
 *
 * <p>So the expressions are parsed straight out of the bytecode and used as the reference.
 */
class RolePermissionScopeTest {

    private static final String CONTROLLERS = "com.novax.leadora.api.controller";

    private static final Pattern ROLE = Pattern.compile("'([A-Z_]+)'");
    private static final Pattern CAN = Pattern.compile("@access\\.can\\('([A-Z_]+)'\\)");

    private static final Set<String> DESKS = Set.of("SALES", "MANAGER", "FO", "FRONT_OFFICE", "RESERVATION");

    /**
     * role → permission codes that some endpoint will actually honour for it.
     *
     * <p>{@code isAuthenticated()} expressions name no role, so the code they guard is reachable by
     * every role — those are credited to all of them.
     */
    private Map<String, Set<String>> pairsFromAnnotations() throws Exception {
        Map<String, Set<String>> pairs = new HashMap<>();
        for (String role : DESKS) {
            pairs.put(role, new HashSet<>());
        }

        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (BeanDefinition bd : scanner.findCandidateComponents(CONTROLLERS)) {
            Class<?> type = Class.forName(bd.getBeanClassName());
            PreAuthorize onClass = type.getAnnotation(PreAuthorize.class);
            credit(pairs, onClass == null ? null : onClass.value());
            for (Method m : type.getDeclaredMethods()) {
                PreAuthorize onMethod = m.getAnnotation(PreAuthorize.class);
                if (onMethod != null) {
                    credit(pairs, onMethod.value());
                }
            }
        }
        return pairs;
    }

    private void credit(Map<String, Set<String>> pairs, String expression) {
        if (expression == null || expression.isBlank()) {
            return;
        }
        Matcher can = CAN.matcher(expression);
        Set<String> codes = new HashSet<>();
        while (can.find()) {
            codes.add(can.group(1));
        }
        if (codes.isEmpty()) {
            return; // role-gated but not permission-gated — tells us nothing about codes
        }

        // Role names appear as quoted literals; strip the ones that are actually the permission
        // codes so hasAnyRole(...) is all that remains.
        Set<String> roles = new HashSet<>();
        Matcher r = ROLE.matcher(expression);
        while (r.find()) {
            String literal = r.group(1);
            if (!codes.contains(literal)) {
                roles.add(literal);
            }
        }
        // No hasAnyRole/hasRole at all => isAuthenticated(): every desk can reach it.
        Set<String> credited = roles.isEmpty() ? DESKS : roles;
        for (String role : credited) {
            pairs.computeIfAbsent(role, k -> new HashSet<>()).addAll(codes);
        }
    }

    @Test
    @DisplayName("Every code declared applicable is one some endpoint honours for that role")
    void declaredScopeIsBackedByAnEndpoint() throws Exception {
        Map<String, Set<String>> pairs = pairsFromAnnotations();

        for (String role : new String[]{"SALES", "MANAGER", "FO", "RESERVATION"}) {
            Set<String> honoured = new HashSet<>(pairs.getOrDefault(role, Set.of()));
            if (role.equals("FO")) {
                // The annotations spell this desk both ways; either spelling proves the function.
                honoured.addAll(pairs.getOrDefault("FRONT_OFFICE", Set.of()));
            }
            Set<String> unbacked = new TreeSet<>(RolePermissionScope.applicableCodes(role));
            unbacked.removeAll(honoured);

            assertThat(unbacked)
                    .as("%s is offered %s in the UC-6.4 grid, but no @PreAuthorize pairs that role "
                            + "with that code — the toggle would save and change nothing", role, unbacked)
                    .isEmpty();
        }
    }

    /**
     * The reverse direction is deliberately not asserted. An endpoint may name a role for a code the
     * scope still withholds, because reachability needs a screen as well as an annotation: Front
     * Office is in {@code PaymentController}'s role list, yet no Front Office route opens the
     * payments screen, so {@code PAYMENT_*} stays out of its scope (and out of SRS §2.2.2's actor
     * list for UC-21.x). Narrower than the annotations is a decision; broader is a bug.
     */
    @Test
    @DisplayName("A role's default grant never exceeds what it can use")
    void defaultsStayInsideTheScope() {
        for (String role : new String[]{"SALES", "MANAGER", "FO", "RESERVATION"}) {
            Set<String> beyond = new TreeSet<>(RolePermissionScope.defaultCodes(role));
            beyond.removeAll(RolePermissionScope.applicableCodes(role));
            assertThat(beyond)
                    .as("Reset would propose %s for %s, which the role cannot exercise", beyond, role)
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("The desks are narrow, and Front Office holds neither payments nor chat")
    void desksStayNarrow() {
        assertThat(RolePermissionScope.applicableCodes("FO"))
                .containsExactlyInAnyOrder("HANDOVER_VIEW", "HANDOVER_WRITE", "NOTIFICATION_VIEW");
        assertThat(RolePermissionScope.applicableCodes("RESERVATION"))
                .doesNotContain("CHAT_VIEW", "LEAD_VIEW", "DEAL_VIEW", "QUOTATION_VIEW");
        // An unmodelled role is offered nothing rather than everything.
        assertThat(RolePermissionScope.applicableCodes("SOMETHING_NEW")).isEmpty();
    }
}
