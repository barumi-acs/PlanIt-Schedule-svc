package com.planit.task.emoji.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/**
 * POST /api/v1/schedules/tasks/{taskId}/emojis 응답 DTO
 */
@Data
@Builder
public class AddEmojiReactionResponse {

    private Long taskEmojiId;
    private Long taskId;
    private Long emojiId;
    private String emojiChar;
    private String userId;
    private LocalDateTime createdAt;
}
