package com.cardplatform.core.funding.controller;

import com.cardplatform.core.funding.dto.TopUpRequest;
import com.cardplatform.core.funding.dto.TopUpResponse;
import com.cardplatform.core.funding.exception.InvalidIdempotencyKeyException;
import com.cardplatform.core.funding.mapper.TopUpMapper;
import com.cardplatform.core.funding.model.TopUp;
import com.cardplatform.core.funding.service.FundingService;
import com.cardplatform.core.shared.money.Money;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/core/topups")
public class TopUpController {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private final FundingService funding;
    private final TopUpMapper mapper;

    public TopUpController(FundingService funding, TopUpMapper mapper) {
        this.funding = funding;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<TopUpResponse> start(@AuthenticationPrincipal Jwt jwt,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                               @Valid @RequestBody TopUpRequest request) {
        if (!IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new InvalidIdempotencyKeyException();
        }
        TopUp topUp = funding.startTopUp(userId(jwt), idempotencyKey, Money.eur(request.amountMinor()),
                request.paymentMethodId());
        // A retry gets the same answer as the first request, status code included
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(topUp));
    }

    @GetMapping("/{topUpId}")
    public TopUpResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID topUpId) {
        return mapper.toResponse(funding.getTopUp(userId(jwt), topUpId));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}