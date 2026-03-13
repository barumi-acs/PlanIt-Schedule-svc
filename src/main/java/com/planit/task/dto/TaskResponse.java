package com.planit.task.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// POST /api/v1/schedules/tasks 응답 DTO
// POST /api/v1/schedules/tasks 응답 DTO
@Getter
@Builder
public class TaskResponse {
    private Long taskId;
    private Long weekGoalsId;
    private String content;
    private boolean complete;
    private String category; // 🎯 카테고리명 (categoryName으로 변경 고려)
    private LocalDate targetDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
