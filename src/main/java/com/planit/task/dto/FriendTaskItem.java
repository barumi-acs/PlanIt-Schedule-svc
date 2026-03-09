package com.planit.task.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class FriendTaskItem {
    private Long taskId;
    private String content;
    private boolean complete;
    private LocalDate targetDate;
    private List<EmojiItem> emojis;
}
