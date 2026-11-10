package com.cardplatform.simulator.controller;

import com.cardplatform.simulator.dto.AmountRequest;
import com.cardplatform.simulator.dto.LoadReport;
import com.cardplatform.simulator.dto.LoadRequest;
import com.cardplatform.simulator.dto.OperationResponse;
import com.cardplatform.simulator.dto.PurchaseRequest;
import com.cardplatform.simulator.dto.PurchaseResponse;
import com.cardplatform.simulator.model.Merchant;
import com.cardplatform.simulator.service.LoadGenerator;
import com.cardplatform.simulator.service.MerchantCatalogue;
import com.cardplatform.simulator.service.PurchaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// A local test tool bound to 127.0.0.1, so it has no authentication of its own
@RestController
@RequestMapping("/api/simulator")
public class SimulatorController {

    private final PurchaseService purchases;
    private final LoadGenerator loadGenerator;
    private final MerchantCatalogue merchants;

    public SimulatorController(PurchaseService purchases, LoadGenerator loadGenerator, MerchantCatalogue merchants) {
        this.purchases = purchases;
        this.loadGenerator = loadGenerator;
        this.merchants = merchants;
    }

    @GetMapping("/merchants")
    public List<Merchant> merchants() {
        return merchants.all();
    }

    @PostMapping("/purchases")
    public PurchaseResponse purchase(@Valid @RequestBody PurchaseRequest request) {
        return purchases.purchase(request);
    }

    @PostMapping("/purchases/{networkTxnId}/clear")
    public OperationResponse clear(@PathVariable String networkTxnId, @Valid @RequestBody AmountRequest request) {
        return purchases.clear(networkTxnId, request.amountMinor());
    }

    @PostMapping("/purchases/{networkTxnId}/reverse")
    public OperationResponse reverse(@PathVariable String networkTxnId) {
        return purchases.reverse(networkTxnId);
    }

    @PostMapping("/purchases/{networkTxnId}/refund")
    public OperationResponse refund(@PathVariable String networkTxnId, @Valid @RequestBody AmountRequest request) {
        return purchases.refund(networkTxnId, request.amountMinor());
    }

    @PostMapping("/load")
    public LoadReport load(@Valid @RequestBody LoadRequest request) {
        return loadGenerator.run(request);
    }
}