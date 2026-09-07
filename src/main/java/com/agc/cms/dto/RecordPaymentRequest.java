package com.agc.cms.dto;

import jakarta.validation.constraints.NotNull;

public class RecordPaymentRequest {

    @NotNull(message = "Payment amount is required")
    private Object paymentAmount;

    public Object getPaymentAmount() {
        return paymentAmount;
    }

    public void setPaymentAmount(Object paymentAmount) {
        this.paymentAmount = paymentAmount;
    }
}
