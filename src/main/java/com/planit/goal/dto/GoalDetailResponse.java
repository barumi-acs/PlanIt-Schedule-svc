package com.planit.goal.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class GoalDetailResponse {
    private Long goalsId;
    private String categoryName; // 🎯 카테고리명 추가
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private int progressRate; // 진행률 (주차 목표 연동 시 계산, 현재 0)
    private List<WeekGoalSummary> weekGoals; // 주차 목표 목록 (주차 목표 API 연동 전 빈 리스트)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Builder
    public static class WeekGoalSummary {
        private Long weekGoalsId;
        private String title;
        private int progressRate;
        private LocalDateTime createdAt;
    }
}
