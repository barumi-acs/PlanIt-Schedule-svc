package com.planit.task.emoji.dto;

import lombok.Builder;
import lombok.Data;

/**
 * GET /api/v1/schedules/emojis 응답 DTO
 */
@Data
@Builder
public class EmojiResponse {

    private Long emojiId;
    private String emojiChar;
    private String name;
}
