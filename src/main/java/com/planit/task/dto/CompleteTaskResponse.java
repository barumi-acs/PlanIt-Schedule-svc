package com.planit.task.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// PATCH /api/v1/schedules/tasks/{taskId}/complete 응답 DTO
@Getter
@Builder
public class CompleteTaskResponse {
    private Long taskId;
    private boolean complete;
    private LocalDateTime updatedAt;
}
