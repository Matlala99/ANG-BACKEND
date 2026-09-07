package com.agc.cms.dto;

import jakarta.validation.constraints.Size;

public class ConcludeCaseRequest {

    private Object verdictID;

    @Size(max = 2000, message = "Verdict text must not exceed 2000 characters")
    private String verdictText;

    public Object getVerdictID() {
        return verdictID;
    }

    public void setVerdictID(Object verdictID) {
        this.verdictID = verdictID;
    }

    public String getVerdictText() {
        return verdictText;
    }

    public void setVerdictText(String verdictText) {
        this.verdictText = verdictText;
    }
}
