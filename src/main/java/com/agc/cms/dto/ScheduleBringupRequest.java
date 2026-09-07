package com.agc.cms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class ScheduleBringupRequest {

    @NotBlank(message = "Bring-up date is required")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Bring-up date must be in YYYY-MM-DD format")
    private String bringUpDate;

    public String getBringUpDate() {
        return bringUpDate;
    }

    public void setBringUpDate(String bringUpDate) {
        this.bringUpDate = bringUpDate;
    }
}
