package com.planit.task.emoji.dto;

import lombok.Builder;
import lombok.Data;

/**
 * GET /api/v1/schedules/tasks/{taskId}/emojis 응답 - 이모지별 그룹 항목
 */
@Data
@Builder
public class TaskEmojiGroupResponse {

    private Long emojiId;
    private String emojiChar;
    private String name;
    /** 해당 이모지에 반응한 총 인원 수 */
    private long count;
    /** 내가 이 이모지에 반응했는지 여부 */
    private boolean myReaction;
    /** 이모지에 반응한 유저 ID 목록 */
    private java.util.List<String> userIds;
    /** 이모지에 반응한 유저 닉네임 목록 */
    private java.util.List<String> nicknames;
}
