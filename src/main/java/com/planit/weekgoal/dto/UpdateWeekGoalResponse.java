package com.planit.weekgoal.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UpdateWeekGoalResponse {
    private Long weekGoalsId;
    private String title;
    private LocalDateTime updatedAt;
}
