package com.cardplatform.core.card.controller;

import com.cardplatform.core.card.dto.CardDetailsResponse;
import com.cardplatform.core.card.dto.CardResponse;
import com.cardplatform.core.card.dto.UpdateControlsRequest;
import com.cardplatform.core.card.mapper.CardMapper;
import com.cardplatform.core.card.service.CardService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/core/cards")
public class CardController {

    private final CardService cardService;
    private final CardMapper cardMapper;

    public CardController(CardService cardService, CardMapper cardMapper) {
        this.cardService = cardService;
        this.cardMapper = cardMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CardResponse issue(@AuthenticationPrincipal Jwt jwt) {
        return cardMapper.toResponse(cardService.issue(userId(jwt)));
    }

    @GetMapping("/me")
    public CardResponse currentCard(@AuthenticationPrincipal Jwt jwt) {
        return cardMapper.toResponse(cardService.currentCard(userId(jwt)));
    }

    // POST, not GET: full card details must never end up in URLs, browser history or caches
    @PostMapping("/me/reveal")
    public ResponseEntity<CardDetailsResponse> reveal(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(cardService.reveal(userId(jwt)));
    }

    @PostMapping("/me/freeze")
    public CardResponse freeze(@AuthenticationPrincipal Jwt jwt) {
        return cardMapper.toResponse(cardService.freeze(userId(jwt)));
    }

    @PostMapping("/me/unfreeze")
    public CardResponse unfreeze(@AuthenticationPrincipal Jwt jwt) {
        return cardMapper.toResponse(cardService.unfreeze(userId(jwt)));
    }

    @PostMapping("/me/cancel")
    public CardResponse cancel(@AuthenticationPrincipal Jwt jwt) {
        return cardMapper.toResponse(cardService.cancel(userId(jwt)));
    }

    @PutMapping("/me/controls")
    public CardResponse updateControls(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody UpdateControlsRequest request) {
        return cardMapper.toResponse(cardService.updateControls(userId(jwt), request));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}