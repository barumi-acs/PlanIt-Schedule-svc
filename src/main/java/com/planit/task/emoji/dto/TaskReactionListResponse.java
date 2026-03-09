package com.planit.task.emoji.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * GET /api/v1/schedules/tasks/{taskId}/emojis 최종 응답 DTO
 */
@Data
@Builder
public class TaskReactionListResponse {

    private Long taskId;
    /** 이모지 종류별 그룹 리스트 (count > 0 인 것만 포함) */
    private List<TaskEmojiGroupResponse> reactions;
}
