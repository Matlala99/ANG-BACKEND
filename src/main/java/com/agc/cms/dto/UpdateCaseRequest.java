package com.agc.cms.dto;

import jakarta.validation.constraints.Size;

public class UpdateCaseRequest {

    @Size(max = 100)
    private String fileNumber;

    @Size(max = 255)
    private String title;

    private Object amount;

    public String getFileNumber() {
        return fileNumber;
    }

    public void setFileNumber(String fileNumber) {
        this.fileNumber = fileNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Object getAmount() {
        return amount;
    }

    public void setAmount(Object amount) {
        this.amount = amount;
    }
}
