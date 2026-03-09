package com.planit.task.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// PATCH /api/v1/schedules/tasks/{taskId} 응답 DTO
@Getter
@Builder
public class UpdateTaskResponse {
    private Long taskId;
    private String content;
    private LocalDateTime updatedAt;
}
