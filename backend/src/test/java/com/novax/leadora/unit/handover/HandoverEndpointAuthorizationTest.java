package com.novax.leadora.unit.handover;
import com.novax.leadora.api.controller.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the role boundary between the two handover surfaces.
 *
 * <p>Front Office reads arrivals through {@code ArrivalHandoverController}, which shows only
 * submitted handovers on a live booking. {@code OperationalHandoverController} applies neither
 * filter, so while it was open to FO the DRAFT handovers the arrival screens hide were one URL
 * swap away. Nothing in the compiler stops somebody adding {@code 'FO'} back to those annotations,
 * so it is asserted here instead.
 */
class HandoverEndpointAuthorizationTest {

    private String preAuthorizeOf(Class<?> type, String method) throws NoSuchMethodException {
        Method m = findMethod(type, method);
        PreAuthorize onMethod = m.getAnnotation(PreAuthorize.class);
        if (onMethod != null) {
            return onMethod.value();
        }
        PreAuthorize onClass = type.getAnnotation(PreAuthorize.class);
        return onClass != null ? onClass.value() : "";
    }

    private Method findMethod(Class<?> type, String name) throws NoSuchMethodException {
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new NoSuchMethodException(type.getSimpleName() + "#" + name);
    }

    @Test
    @DisplayName("The operational read endpoints must not name Front Office")
    void operationalReadsExcludeFrontOffice() throws Exception {
        for (String endpoint : new String[]{"list", "detail"}) {
            String expression = preAuthorizeOf(OperationalHandoverController.class, endpoint);
            assertThat(expression)
                    .as("OperationalHandoverController#%s exposes DRAFT handovers, so it must "
                            + "not be reachable by Front Office", endpoint)
                    .doesNotContain("'FO'")
                    .doesNotContain("FRONT_OFFICE");
            assertThat(expression).contains("HANDOVER_VIEW");
        }
    }

    @Test
    @DisplayName("The operational write endpoints stay Sales/Reservation only")
    void operationalWritesExcludeFrontOffice() throws Exception {
        for (String endpoint : new String[]{"create", "update"}) {
            String expression = preAuthorizeOf(OperationalHandoverController.class, endpoint);
            assertThat(expression).doesNotContain("'FO'").doesNotContain("FRONT_OFFICE");
            assertThat(expression).contains("HANDOVER_WRITE");
        }
    }

    @Test
    @DisplayName("The arrival desk is Front Office's surface and stays permission-gated")
    void arrivalEndpointsAllowFrontOffice() throws Exception {
        String klass = ArrivalHandoverController.class.getAnnotation(PreAuthorize.class).value();
        assertThat(klass).contains("'FO'").contains("HANDOVER_VIEW");

        String readiness = preAuthorizeOf(ArrivalHandoverController.class, "updateReadiness");
        assertThat(readiness).contains("'FO'").contains("HANDOVER_WRITE");
    }
}
