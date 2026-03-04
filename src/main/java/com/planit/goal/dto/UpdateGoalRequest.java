package com.planit.goal.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateGoalRequest {
    private String title;
    private String startDate; // yyyy-MM-dd
    private String endDate; // yyyy-MM-dd
}
