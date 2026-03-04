package com.planit.weekgoal.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// GET /api/v1/schedules/goals/{goalsId}/week-goals 응답 DTO (목록 단일 항목)
@Getter
@Builder
public class WeekGoalListItem {
    private Long weekGoalsId;
    private String title;
    private int progressRate; // TODO: 일일 태스크 연동 후 계산, 현재 0
    private LocalDateTime createdAt;
}
