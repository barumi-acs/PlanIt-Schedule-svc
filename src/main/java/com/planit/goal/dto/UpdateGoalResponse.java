package com.planit.goal.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class UpdateGoalResponse {
    private Long goalsId;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime updatedAt;
}
