package com.agc.cms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class RespondCaseRequest {

    @NotBlank(message = "Response action is required")
    @Pattern(regexp = "^(accept|decline)$", message = "Response must be either 'accept' or 'decline'")
    private String response;

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }
}
