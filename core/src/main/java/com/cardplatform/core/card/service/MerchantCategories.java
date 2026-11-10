package com.cardplatform.core.card.service;

import com.cardplatform.core.card.api.MerchantCategory;

final class MerchantCategories {

    private MerchantCategories() {
    }

    // A simplified slice of ISO 18245 merchant category codes; anything not listed is OTHER
    static MerchantCategory fromMcc(String mcc) {
        if (mcc == null || !mcc.matches("\\d{4}")) {
            return MerchantCategory.OTHER;
        }
        int code = Integer.parseInt(mcc);
        if (code >= 3000 && code <= 3999) {
            return MerchantCategory.TRAVEL; // airline, hotel and car rental chains
        }
        return switch (code) {
            case 5411, 5422, 5441, 5451, 5462, 5499 -> MerchantCategory.GROCERIES;
            case 5812, 5813, 5814 -> MerchantCategory.RESTAURANTS;
            case 4111, 4121, 4131, 4789, 5541, 5542 -> MerchantCategory.TRANSPORT;
            case 4511, 4722, 7011, 7512 -> MerchantCategory.TRAVEL;
            case 7832, 7922, 7991, 7996 -> MerchantCategory.ENTERTAINMENT;
            case 5311, 5651, 5691, 5732, 5942, 5999 -> MerchantCategory.RETAIL;
            case 5815, 5816, 5817, 5818 -> MerchantCategory.DIGITAL_SERVICES;
            case 7995 -> MerchantCategory.GAMBLING;
            default -> MerchantCategory.OTHER;
        };
    }
}