package com.cardplatform.core.card.mapper;

import com.cardplatform.core.card.dto.CardDetailsResponse;
import com.cardplatform.core.card.dto.CardResponse;
import com.cardplatform.core.card.model.Card;
import org.springframework.stereotype.Component;

import java.util.TreeSet;

@Component
public class CardMapper {

    public CardResponse toResponse(Card card) {
        return new CardResponse(card.getId(), "**** **** **** " + card.getLastFour(),
                card.getExpiryMonth(), card.getExpiryYear(), card.getStatus().name(),
                card.getPerTransactionLimitMinor(), new TreeSet<>(card.getBlockedCategories()),
                card.getCreatedAt());
    }

    public CardDetailsResponse toDetails(Card card, String pan, String cvv) {
        return new CardDetailsResponse(pan, card.getExpiryMonth(), card.getExpiryYear(), cvv);
    }
}