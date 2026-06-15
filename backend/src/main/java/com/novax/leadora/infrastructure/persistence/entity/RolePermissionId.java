package com.novax.leadora.infrastructure.persistence.entity;

import java.io.Serializable;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissionId implements Serializable {
    private Integer role;
    private Integer permission;
}
