package com.agc.cms.security;

public class AuthenticatedUser {
    private final int officerID;
    private final String username;
    private final String email;
    private final String role;
    private final int userType;
    private final boolean isSupervisor;
    private final Integer supervisorID;
    private final String fullName;

    public AuthenticatedUser(int officerID, String username, String email, String role, int userType, boolean isSupervisor, Integer supervisorID, String fullName) {
        this.officerID = officerID;
        this.username = username;
        this.email = email;
        this.role = role;
        this.userType = userType;
        this.isSupervisor = isSupervisor;
        this.supervisorID = supervisorID;
        this.fullName = fullName;
    }

    public AuthenticatedUser(int officerID, String username, String email, String role, int userType, boolean isSupervisor, Integer supervisorID) {
        this(officerID, username, email, role, userType, isSupervisor, supervisorID, null);
    }

    public AuthenticatedUser(int officerID, String username, String email, String role, int userType) {
        this(officerID, username, email, role, userType, false, null, null);
    }

    public int getOfficerID() {
        return officerID;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return (fullName != null && !fullName.trim().isEmpty()) ? fullName : username;
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
        return userType == 2 || userType == 4;
    }

    public boolean isSheriff() {
        return userType == 3;
    }

    public boolean isAllocatingOfficer() {
        return isRegistry();
    }

    public boolean isStateCounsel() {
        return userType == 7;
    }

    public boolean isSupervisor() {
        return isSupervisor;
    }

    public Integer getSupervisorID() {
        return supervisorID;
    }
}
