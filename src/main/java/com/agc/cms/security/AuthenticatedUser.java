package com.agc.cms.security;

public class AuthenticatedUser {
    private final int officerID;
    private final String username;
    private final String email;
    private final String role;
    private final int userType;

    public AuthenticatedUser(int officerID, String username, String email, String role, int userType) {
        this.officerID = officerID;
        this.username = username;
        this.email = email;
        this.role = role;
        this.userType = userType;
    }

    public int getOfficerID() {
        return officerID;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public int getUserType() {
        return userType;
    }

    public boolean isAdmin() {
        return userType == 1;
    }

    public boolean isRegistry() {
        return userType == 2;
    }

    public boolean isSheriff() {
        return userType == 3;
    }

    public boolean isAllocatingOfficer() {
        return userType == 4;
    }

    public boolean isStateCounsel() {
        return userType == 7;
    }
}
