package com.cardplatform.history.service;

import com.cardplatform.history.dto.CategorySpending;
import com.cardplatform.history.dto.SpendingResponse;
import com.cardplatform.history.model.Transaction;
import com.cardplatform.history.model.TransactionStatus;
import com.cardplatform.history.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class HistoryQueryService {

    private final TransactionRepository transactions;

    public HistoryQueryService(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    // The user filter always applies, so a customer can only ever see their own transactions
    @Transactional(readOnly = true)
    public Page<Transaction> list(UUID userId, TransactionStatus status, String category, LocalDate from,
                                  LocalDate to, Pageable pageable) {
        Specification<Transaction> filter = (root, query, cb) -> cb.equal(root.get("userId"), userId);
        if (status != null) {
            filter = filter.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (category != null) {
            filter = filter.and((root, query, cb) -> cb.equal(root.get("category"), category));
        }
        if (from != null) {
            Instant start = startOfDay(from);
            filter = filter.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), start));
        }
        if (to != null) {
            Instant end = startOfDay(to.plusDays(1));
            filter = filter.and((root, query, cb) -> cb.lessThan(root.<Instant>get("occurredAt"), end));
        }
        return transactions.findAll(filter, pageable);
    }

    // Completed card payments minus refunds, per category, for one calendar month (UTC)
    @Transactional(readOnly = true)
    public SpendingResponse monthlySpending(UUID userId, YearMonth month) {
        Instant from = startOfDay(month.atDay(1));
        Instant to = startOfDay(month.plusMonths(1).atDay(1));

        Map<String, Long> totals = new HashMap<>();
        for (Transaction transaction : transactions.findByUserIdAndStatusAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                userId, TransactionStatus.COMPLETED, from, to)) {
            long signedAmount = switch (transaction.getType()) {
                case CARD_PAYMENT -> transaction.getAmountMinor();
                case REFUND -> -transaction.getAmountMinor();
                case TOP_UP -> 0;
            };
            if (signedAmount != 0) {
                totals.merge(transaction.getCategory(), signedAmount, Long::sum);
            }
        }

        List<CategorySpending> categories = totals.entrySet().stream()
                .map(entry -> new CategorySpending(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(CategorySpending::amountMinor).reversed())
                .toList();
        long total = categories.stream().mapToLong(CategorySpending::amountMinor).sum();
        return new SpendingResponse(month.toString(), "EUR", total, categories);
    }

    private static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}