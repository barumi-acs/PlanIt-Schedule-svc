package com.planit.task.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// PATCH /api/v1/schedules/tasks/{taskId}/postpone 응답 DTO
@Getter
@Builder
public class PostponeTaskResponse {
    private Long taskId;
    private String content;
    private LocalDate targetDate;
    private LocalDateTime updatedAt;
}
