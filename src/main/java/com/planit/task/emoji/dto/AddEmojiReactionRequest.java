package com.planit.task.emoji.dto;

import lombok.Data;

/**
 * POST /api/v1/schedules/tasks/{taskId}/emojis 요청 DTO
 */
@Data
public class AddEmojiReactionRequest {

    private Long emojiId;
}
