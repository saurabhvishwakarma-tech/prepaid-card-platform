package com.cardplatform.core.funding.mapper;

import com.cardplatform.core.funding.dto.TopUpResponse;
import com.cardplatform.core.funding.model.TopUp;
import org.springframework.stereotype.Component;

@Component
public class TopUpMapper {

    public TopUpResponse toResponse(TopUp topUp) {
        return new TopUpResponse(topUp.getId(), topUp.getAmountMinor(), topUp.getCurrency(),
                topUp.getStatus().name(), topUp.getFailureCode(), topUp.getCreatedAt());
    }
}