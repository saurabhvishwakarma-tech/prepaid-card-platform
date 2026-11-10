package com.cardplatform.history.dto;

import java.util.List;

public record SpendingResponse(String month, String currency, long totalMinor, List<CategorySpending> categories) {
}