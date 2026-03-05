package com.planit.task.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class FriendTaskResponse {
    private String friendUserId;
    private LocalDate targetDate;
    private List<FriendTaskItem> tasks;
}
