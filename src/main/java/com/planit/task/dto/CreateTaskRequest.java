package com.planit.task.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateTaskRequest {
    private Long weekGoalsId;
    private String content;
    private String targetDate; // yyyy-MM-dd
}
