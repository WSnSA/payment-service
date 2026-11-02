package com.wsnsa.payment.qpay.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** Хуудаслалт: page_number, page_limit (1-100). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Offset(int pageNumber, int pageLimit) {

    public static Offset firstPage() {
        return new Offset(1, 100);
    }
}
