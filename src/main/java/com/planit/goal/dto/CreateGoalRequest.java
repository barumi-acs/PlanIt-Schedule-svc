package com.planit.goal.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateGoalRequest {
    private String category; // category_list.name → category_id 변환
    private String title;
    private String startDate; // yyyy-MM-dd
    private String endDate; // yyyy-MM-dd
}
