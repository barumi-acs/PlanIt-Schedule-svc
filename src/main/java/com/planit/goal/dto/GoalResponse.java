package com.planit.goal.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class GoalResponse {
    private Long goalsId;
    private Long categoryId;
    private String categoryName; // 🎯 카테고리명 추가
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
