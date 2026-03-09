package com.planit.weekgoal.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// POST /api/v1/schedules/goals/{goalsId}/week-goals 응답 DTO
@Getter
@Builder
public class WeekGoalResponse {
    private Long weekGoalsId;
    private Long goalsId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
