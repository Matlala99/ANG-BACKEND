package com.agc.cms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateCaseRequest {

    @NotBlank(message = "Case type is required")
    @Size(max = 50)
    private String caseType;

    @NotBlank(message = "File number is required")
    @Size(min = 3, max = 100, message = "File number must be between 3 and 100 characters")
    private String fileNumber;

    @NotBlank(message = "Case title is required")
    @Size(min = 3, max = 255, message = "Case title must be between 3 and 255 characters")
    private String title;

    private Object amount;

    @Size(max = 255)
    private String ministryName;

    private Object counselID;

    public String getCaseType() {
        return caseType;
    }

    public void setCaseType(String caseType) {
        this.caseType = caseType;
    }

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

    public String getMinistryName() {
        return ministryName;
    }

    public void setMinistryName(String ministryName) {
        this.ministryName = ministryName;
    }

    public Object getCounselID() {
        return counselID;
    }

    public void setCounselID(Object counselID) {
        this.counselID = counselID;
    }
}
