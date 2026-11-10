package com.cardplatform.simulator.service;

import com.cardplatform.simulator.dto.LoadReport;
import com.cardplatform.simulator.dto.LoadRequest;
import com.cardplatform.simulator.dto.PurchaseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

// Fires many purchases at once against one card, so locking and retries can be watched under pressure
@Service
public class LoadGenerator {

    private static final Logger log = LoggerFactory.getLogger(LoadGenerator.class);

    private final PurchaseService purchases;

    public LoadGenerator(PurchaseService purchases) {
        this.purchases = purchases;
    }

    public LoadReport run(LoadRequest request) {
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger approved = new AtomicInteger();
        AtomicInteger declined = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        long start = System.nanoTime();

        try (ExecutorService pool = Executors.newFixedThreadPool(request.concurrency())) {
            for (int i = 0; i < request.count(); i++) {
                pool.submit(() -> {
                    try {
                        PurchaseResponse response = purchases.purchase(request.toPurchase());
                        latencies.add(response.latencyMs());
                        switch (response.decision()) {
                            case "APPROVED" -> approved.incrementAndGet();
                            case "DECLINED" -> declined.incrementAndGet();
                            default -> failed.incrementAndGet();
                        }
                    } catch (RuntimeException e) {
                        failed.incrementAndGet();
                    }
                });
            }
        } // closing the pool waits for every purchase to finish

        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        LoadReport report = new LoadReport(request.count(), approved.get(), declined.get(), failed.get(),
                percentile(sorted, 50), percentile(sorted, 95),
                sorted.isEmpty() ? 0 : sorted.getLast(), (System.nanoTime() - start) / 1_000_000);
        log.info("Load run finished: {}", report);
        return report;
    }

    // Nearest-rank percentile: the smallest latency that at least p% of calls stayed within
    private static long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(index, 0));
    }
}