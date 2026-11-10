package com.cardplatform.simulator.service;

import com.cardplatform.simulator.exception.UnknownMerchantException;
import com.cardplatform.simulator.model.Merchant;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class MerchantCatalogue {

    // Fictional merchants, one per spending category, so card controls can be tried out
    private static final List<Merchant> MERCHANTS = List.of(
            new Merchant("corner-shop", "Corner Shop", "5411", "IE"),
            new Merchant("harbour-cafe", "Harbour Cafe", "5812", "IE"),
            new Merchant("city-rail", "City Rail", "4111", "IE"),
            new Merchant("sky-air", "Sky Air", "4511", "IE"),
            new Merchant("game-store", "Game Store", "5816", "GB"),
            new Merchant("lucky-bet", "Lucky Bet", "7995", "MT"));

    private final Map<String, Merchant> byId = MERCHANTS.stream()
            .collect(Collectors.toUnmodifiableMap(Merchant::id, Function.identity()));

    public List<Merchant> all() {
        return MERCHANTS;
    }

    public Merchant get(String merchantId) {
        Merchant merchant = byId.get(merchantId);
        if (merchant == null) {
            throw new UnknownMerchantException(merchantId);
        }
        return merchant;
    }
}