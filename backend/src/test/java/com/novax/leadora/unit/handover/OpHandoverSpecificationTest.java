package com.novax.leadora.unit.handover;

import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The handover specifications address entity attributes by <b>string name</b>
 * ({@code root.join("createdBy")}, {@code booking.get("assignedUser")}). javac
 * cannot check those,
 * so a renamed or mistyped field compiles cleanly and then throws
 * {@code IllegalArgumentException: Unable to locate Attribute} on the first
 * request — after
 * deployment, on a screen nobody re-tested.
 *
 * <p>
 * With no integration-test database in this project, this is the cheapest
 * automated guard: it
 * pins every attribute path the specifications actually walk. Rename a field
 * and this goes red
 * instead of production.
 */
class OpHandoverSpecificationTest {

    @ParameterizedTest(name = "{0}.{1} exists")
    @CsvSource({
            // forFrontOffice + forOperations, on the handover root
            "OpHandoverEntity, status",
            "OpHandoverEntity, readinessStatus",
            "OpHandoverEntity, booking",
            "OpHandoverEntity, createdBy",
            "OpHandoverEntity, assignedFoUserId",
            // reached through the booking join
            "BookingEntity,    status",
            "BookingEntity,    checkInDate",
            "BookingEntity,    bookingCode",
            "BookingEntity,    customer",
            "BookingEntity,    assignedUser",
            // leaf attributes of the ownership predicate and the free-text search
            "UserEntity,       userId",
            "CustomerEntity,   fullName",
    })
    @DisplayName("Every attribute path the specifications walk resolves to a real field")
    void attributePathsResolve(String entity, String attribute) {
        Class<?> type = switch (entity) {
            case "OpHandoverEntity" -> OpHandoverEntity.class;
            case "BookingEntity" -> BookingEntity.class;
            case "CustomerEntity" -> CustomerEntity.class;
            case "UserEntity" -> UserEntity.class;
            default -> throw new IllegalArgumentException("Unmapped entity " + entity);
        };

        assertThat(declaredField(type, attribute))
                .as("%s has no field '%s' — a specification referencing it by name would throw "
                        + "at runtime, not at compile time", entity, attribute)
                .isNotNull();
    }

    /**
     * Walks up the hierarchy so fields inherited from {@code BaseEntity} count too.
     */
    private Field declaredField(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep walking
            }
        }
        return null;
    }
}
