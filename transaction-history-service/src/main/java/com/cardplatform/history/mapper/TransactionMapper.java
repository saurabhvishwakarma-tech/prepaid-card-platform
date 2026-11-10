package com.cardplatform.history.mapper;

import com.cardplatform.history.dto.TransactionPage;
import com.cardplatform.history.dto.TransactionResponse;
import com.cardplatform.history.model.Transaction;
import com.cardplatform.history.model.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction transaction) {
        String direction = transaction.getType() == TransactionType.CARD_PAYMENT ? "DEBIT" : "CREDIT";
        return new TransactionResponse(transaction.getId(), transaction.getType().name(), direction,
                transaction.getStatus().name(), transaction.getAmountMinor(), transaction.getCurrency(),
                transaction.getMerchantName(), transaction.getCategory(), transaction.getDeclineReason(),
                transaction.getOccurredAt());
    }

    public TransactionPage toPage(Page<Transaction> page) {
        return new TransactionPage(page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}