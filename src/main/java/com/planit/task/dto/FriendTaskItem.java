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
    private String category;
    private Long weekGoalsId;
    private String weekGoalsTitle;
    private String goalTitle; // 🎯 월간 목표 제목 추가
    private List<EmojiItem> emojis;
}
