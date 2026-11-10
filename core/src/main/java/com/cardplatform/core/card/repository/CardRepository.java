package com.cardplatform.core.card.repository;

import com.cardplatform.core.card.model.Card;
import com.cardplatform.core.card.model.CardStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {

    // The user's current card: the one that isn't cancelled
    Optional<Card> findByUserIdAndStatusNot(UUID userId, CardStatus status);

    Optional<Card> findByPanHash(String panHash);

    boolean existsByPanHash(String panHash);
}