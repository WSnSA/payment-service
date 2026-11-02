package com.wsnsa.payment.web;

import jakarta.validation.constraints.Size;

public record RefundRequest(@Size(max = 255) String note) {
}
