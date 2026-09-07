package com.agc.cms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 2, max = 100, message = "Username must be between 2 and 100 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 1, max = 255, message = "Password is required")
    private String password;

    // Honeypot field for bot protection (should be empty when submitted by legitimate humans)
    private String _hp_trap;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String get_hp_trap() {
        return _hp_trap;
    }

    public void set_hp_trap(String _hp_trap) {
        this._hp_trap = _hp_trap;
    }
}
