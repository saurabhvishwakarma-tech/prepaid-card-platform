package com.cardplatform.history.controller;

import com.cardplatform.history.dto.SpendingResponse;
import com.cardplatform.history.dto.TransactionPage;
import com.cardplatform.history.mapper.TransactionMapper;
import com.cardplatform.history.model.TransactionStatus;
import com.cardplatform.history.service.HistoryQueryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private static final int MAX_PAGE_SIZE = 100;

    private final HistoryQueryService history;
    private final TransactionMapper mapper;

    public HistoryController(HistoryQueryService history, TransactionMapper mapper) {
        this.history = history;
        this.mapper = mapper;
    }

    @GetMapping("/transactions")
    public TransactionPage transactions(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam(required = false) TransactionStatus status,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "occurredAt"));
        return mapper.toPage(history.list(userId(jwt), status, category, from, to, pageRequest));
    }

    // month as yyyy-MM, for example 2026-11
    @GetMapping("/spending")
    public SpendingResponse spending(@AuthenticationPrincipal Jwt jwt, @RequestParam YearMonth month) {
        return history.monthlySpending(userId(jwt), month);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}