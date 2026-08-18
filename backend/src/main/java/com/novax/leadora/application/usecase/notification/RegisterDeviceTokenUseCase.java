package com.novax.leadora.application.usecase.notification;

import com.novax.leadora.infrastructure.persistence.entity.UserDeviceTokenEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.UserDeviceTokenRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegisterDeviceTokenUseCase {

    private final UserDeviceTokenRepository userDeviceTokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public void register(UUID userId, String fcmToken, String deviceInfo) {
        if (fcmToken == null || fcmToken.trim().isEmpty()) {
            return;
        }
        
        Optional<UserDeviceTokenEntity> existing = 
            userDeviceTokenRepository.findByUser_UserIdAndFcmToken(userId, fcmToken);
            
        if (existing.isPresent()) {
            UserDeviceTokenEntity entity = existing.get();
            entity.setDeviceInfo(deviceInfo);
            userDeviceTokenRepository.save(entity);
        } else {
            UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
                
            UserDeviceTokenEntity newEntity = UserDeviceTokenEntity.builder()
                .user(user)
                .fcmToken(fcmToken)
                .deviceInfo(deviceInfo)
                .build();
            userDeviceTokenRepository.save(newEntity);
        }
    }

    @Transactional
    public void unregister(UUID userId, String fcmToken) {
        if (fcmToken == null || fcmToken.trim().isEmpty()) {
            return;
        }
        userDeviceTokenRepository.deleteByUser_UserIdAndFcmToken(userId, fcmToken);
    }
}
