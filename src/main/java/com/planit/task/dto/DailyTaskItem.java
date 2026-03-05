package com.planit.task.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

// 일간 할 일 목록 단일 항목 DTO
@Getter
@Builder
public class DailyTaskItem {
    private Long taskId;
    private Long weekGoalsId;
    private String weekGoalsTitle;
    private String content;
    private boolean complete;
    private LocalDate targetDate;
}
