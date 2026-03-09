package com.planit.task.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

// GET /api/v1/schedules/tasks/daily 응답 DTO
@Getter
@Builder
public class DailyTaskResponse {
    private LocalDate targetDate;
    private int totalCount;
    private int completedCount;
    private int progressRate; // completedCount / totalCount * 100 (소수점 버림)
    private List<DailyTaskItem> tasks;
}
