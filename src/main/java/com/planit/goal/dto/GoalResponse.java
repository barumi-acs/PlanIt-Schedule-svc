package com.planit.goal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class GoalResponse {
    private Long goalsId;

    @JsonProperty("list_id")
    private Long listId;

    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
