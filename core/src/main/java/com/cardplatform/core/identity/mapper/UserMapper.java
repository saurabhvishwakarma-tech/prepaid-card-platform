package com.cardplatform.core.identity.mapper;

import com.cardplatform.core.identity.api.UserSummary;
import com.cardplatform.core.identity.dto.UserResponse;
import com.cardplatform.core.identity.model.KycStatus;
import com.cardplatform.core.identity.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole().name(), user.getKycStatus().name(), user.getCreatedAt());
    }

    public UserSummary toSummary(User user) {
        return new UserSummary(user.getId(), user.getFullName(), user.getKycStatus() == KycStatus.VERIFIED);
    }
}