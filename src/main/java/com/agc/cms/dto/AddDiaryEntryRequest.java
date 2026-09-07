package com.agc.cms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AddDiaryEntryRequest {

    @NotBlank(message = "Case ID is required")
    private String case_id;

    @NotBlank(message = "Event type is required")
    @Size(max = 100)
    private String event_type;

    @NotBlank(message = "Event date is required")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Event date must be in YYYY-MM-DD format")
    private String event_date;

    private String event_time;

    @Size(max = 255)
    private String location;

    @Size(max = 2000)
    private String description;

    private Object assigned_user_id;

    public String getCase_id() {
        return case_id;
    }

    public void setCase_id(String case_id) {
        this.case_id = case_id;
    }

    public String getEvent_type() {
        return event_type;
    }

    public void setEvent_type(String event_type) {
        this.event_type = event_type;
    }

    public String getEvent_date() {
        return event_date;
    }

    public void setEvent_date(String event_date) {
        this.event_date = event_date;
    }

    public String getEvent_time() {
        return event_time;
    }

    public void setEvent_time(String event_time) {
        this.event_time = event_time;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Object getAssigned_user_id() {
        return assigned_user_id;
    }

    public void setAssigned_user_id(Object assigned_user_id) {
        this.assigned_user_id = assigned_user_id;
    }
}
