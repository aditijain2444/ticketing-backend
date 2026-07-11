package com.ticketing.dto;

public class HoldRequest {
    private Long userId; // later this comes from the logged-in user's JWT, for now we pass it manually

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}