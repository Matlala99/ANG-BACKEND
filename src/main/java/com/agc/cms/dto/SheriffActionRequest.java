package com.agc.cms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SheriffActionRequest {

    @NotBlank(message = "Action type is required")
    private String action_type;

    private Object sheriff_id;

    @Size(max = 200)
    private String defendant_name;

    @Size(max = 500)
    private String defendant_address;

    @Size(max = 2000)
    private String notes;

    @Size(max = 2000)
    private String instructions;

    @Size(max = 100)
    private String writ_type;

    public String getAction_type() {
        return action_type;
    }

    public void setAction_type(String action_type) {
        this.action_type = action_type;
    }

    public Object getSheriff_id() {
        return sheriff_id;
    }

    public void setSheriff_id(Object sheriff_id) {
        this.sheriff_id = sheriff_id;
    }

    public String getDefendant_name() {
        return defendant_name;
    }

    public void setDefendant_name(String defendant_name) {
        this.defendant_name = defendant_name;
    }

    public String getDefendant_address() {
        return defendant_address;
    }

    public void setDefendant_address(String defendant_address) {
        this.defendant_address = defendant_address;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public String getWrit_type() {
        return writ_type;
    }

    public void setWrit_type(String writ_type) {
        this.writ_type = writ_type;
    }
}
