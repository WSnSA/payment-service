package com.wsnsa.payment.qpay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentListResponse(Integer count, List<JsonNode> rows) {
}
