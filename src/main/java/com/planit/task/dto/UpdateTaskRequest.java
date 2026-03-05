package com.planit.task.dto;

import lombok.Getter;

// PATCH /api/v1/schedules/tasks/{taskId} 요청 DTO
@Getter
public class UpdateTaskRequest {
    private String content;
}
