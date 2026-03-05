package com.planit.task.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EmojiItem {
    private String emojiId; // emoji_type 값 ("1", "2" 등)
    private int count; // 해당 이모지로 반응한 사용자 수
    private boolean myReaction; // 나(요청자)가 이 이모지로 반응했는지 여부
}
