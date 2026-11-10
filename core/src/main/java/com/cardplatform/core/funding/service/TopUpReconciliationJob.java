package com.cardplatform.core.funding.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
class TopUpReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(TopUpReconciliationJob.class);

    private final FundingService funding;
    private final Duration reconcileAfter;

    TopUpReconciliationJob(FundingService funding,
                           @Value("${cardplatform.funding.reconcile-after}") Duration reconcileAfter) {
        this.funding = funding;
        this.reconcileAfter = reconcileAfter;
    }

    @Scheduled(fixedDelayString = "${cardplatform.funding.reconcile-interval}")
    void reconcile() {
        int settled = funding.reconcilePendingBefore(Instant.now().minus(reconcileAfter));
        if (settled > 0) {
            log.info("Reconciliation settled {} top-ups whose webhook hadn't arrived", settled);
        }
    }
}