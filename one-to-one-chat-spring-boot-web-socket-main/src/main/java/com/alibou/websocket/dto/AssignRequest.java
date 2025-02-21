package com.alibou.websocket.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignRequest {

    private String managerId;
    private String customerId;

    public String getManagerId() {
        return managerId;
    }

    public String getCustomerId() {
        return customerId;
    }

    // Setters
    public void setManagerId(String managerId) {
        this.managerId = managerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

}
