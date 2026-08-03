package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.UserDeviceTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceTokenEntity, UUID> {
    List<UserDeviceTokenEntity> findByUser_UserId(UUID userId);
    Optional<UserDeviceTokenEntity> findByUser_UserIdAndFcmToken(UUID userId, String fcmToken);
    void deleteByFcmToken(String fcmToken);
    void deleteByUser_UserIdAndFcmToken(UUID userId, String fcmToken);
}
