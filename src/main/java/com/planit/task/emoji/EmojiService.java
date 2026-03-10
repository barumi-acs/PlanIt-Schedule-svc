package com.planit.task.emoji;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planit.global.CustomException;
import com.planit.global.ErrorCode;
import com.planit.grpc.UserServiceGrpcClient;
import com.planit.task.TaskData;
import com.planit.task.TaskRepository;
import com.planit.task.emoji.dto.AddEmojiReactionRequest;
import com.planit.task.emoji.dto.AddEmojiReactionResponse;
import com.planit.task.emoji.dto.EmojiResponse;
import com.planit.task.emoji.dto.TaskEmojiGroupResponse;
import com.planit.task.emoji.dto.TaskReactionListResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmojiService {

    private final EmojiRepository emojiRepository;
    private final TaskRepository taskRepository;
    private final TaskEmojiRepository taskEmojiRepository;
    private final UserServiceGrpcClient userServiceGrpcClient;

    /**
     * GET /api/v1/schedules/emojis - 이모지 목록 전체 조회
     */
    @Transactional(readOnly = true)
    public List<EmojiResponse> getEmojiList() {
        return emojiRepository.findAll().stream()
                .map(emoji -> EmojiResponse.builder()
                        .emojiId(emoji.getEmojiId())
                        .emojiChar(emoji.getEmojiChar())
                        .name(emoji.getName())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * GET /api/v1/schedules/tasks/{taskId}/emojis - 할 일 이모지 반응 목록 조회
     * 이모지 종류별로 그룹핑하여 count 및 내 반응 여부(myReaction) 포함하여 반환
     */
    @Transactional(readOnly = true)
    public TaskReactionListResponse getReactions(Long taskId, String userId) {
        // 할 일 존재 확인
        taskRepository.findById(taskId)
                .orElseThrow(() -> new CustomException(ErrorCode.S4041));

        // 해당 할 일의 전체 리액션 조회
        List<TaskEmojiData> reactions = taskEmojiRepository.findByTask_TaskId(taskId);

        // 전체 리액션에 포함된 유저 ID 목록 (중복 제거)
        List<String> allUserIds = reactions.stream()
                .map(TaskEmojiData::getUserId)
                .distinct()
                .collect(Collectors.toList());

        // gRPC를 통해 닉네임 일괄 조회 (오류 발생 시 빈 맵 반환하여 서비스 중단 방지)
        Map<String, String> fetchedNamesMap;
        try {
            fetchedNamesMap = userServiceGrpcClient.getUserNames(allUserIds);
        } catch (Exception e) {
            System.err.println("[EmojiService] 닉네임 조회 실패 (gRPC 오류): " + e.getMessage());
            fetchedNamesMap = java.util.Collections.emptyMap();
        }
        final Map<String, String> userNamesMap = fetchedNamesMap;

        // emojiId 기준으로 그룹핑
        Map<Long, List<TaskEmojiData>> grouped = reactions.stream()
                .collect(Collectors.groupingBy(r -> r.getEmoji().getEmojiId()));

        List<TaskEmojiGroupResponse> groupedReactions = grouped.entrySet().stream()
                .map(entry -> {
                    EmojiData emoji = entry.getValue().get(0).getEmoji();
                    long count = entry.getValue().size();
                    boolean myReaction = entry.getValue().stream()
                            .anyMatch(r -> userId.equals(r.getUserId()));
                    
                    List<String> userIds = entry.getValue().stream()
                            .map(TaskEmojiData::getUserId)
                            .collect(Collectors.toList());

                    List<String> nicknames = userIds.stream()
                            .map(id -> userNamesMap.getOrDefault(id, "알 수 없는 사용자"))
                            .collect(Collectors.toList());

                    return TaskEmojiGroupResponse.builder()
                            .emojiId(emoji.getEmojiId())
                            .emojiChar(emoji.getEmojiChar())
                            .name(emoji.getName())
                            .count(count)
                            .myReaction(myReaction)
                            .userIds(userIds)
                            .nicknames(nicknames)
                            .build();
                })
                // emojiId 오름차순 정렬로 일관된 순서 보장
                .sorted((a, b) -> Long.compare(a.getEmojiId(), b.getEmojiId()))
                .collect(Collectors.toList());

        return TaskReactionListResponse.builder()
                .taskId(taskId)
                .reactions(groupedReactions)
                .build();
    }

    /**
     * POST /api/v1/schedules/tasks/{taskId}/emojis - 이모지 리액션 등록
     */
    @Transactional
    public AddEmojiReactionResponse addReaction(Long taskId, AddEmojiReactionRequest req, String userId) {
        // 할 일 존재 확인
        TaskData task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CustomException(ErrorCode.S4041));

        // 이모지 존재 확인
        EmojiData emoji = emojiRepository.findById(req.getEmojiId())
                .orElseThrow(() -> new CustomException(ErrorCode.S4043));

        // 중복 반응 방지: 이미 반응이 있다면 기존 반응 반환
        return taskEmojiRepository.findByTask_TaskIdAndEmoji_EmojiIdAndUserId(taskId, req.getEmojiId(), userId)
                .map(existing -> AddEmojiReactionResponse.builder()
                        .taskEmojiId(existing.getTaskEmojiId())
                        .taskId(taskId)
                        .emojiId(emoji.getEmojiId())
                        .emojiChar(emoji.getEmojiChar())
                        .userId(userId)
                        .createdAt(existing.getCreatedAt())
                        .build())
                .orElseGet(() -> {
                    // 새 리액션 저장
                    TaskEmojiData taskEmoji = new TaskEmojiData();
                    taskEmoji.setTask(task);
                    taskEmoji.setEmoji(emoji);
                    taskEmoji.setUserId(userId);
                    TaskEmojiData saved = taskEmojiRepository.save(taskEmoji);

                    return AddEmojiReactionResponse.builder()
                            .taskEmojiId(saved.getTaskEmojiId())
                            .taskId(taskId)
                            .emojiId(emoji.getEmojiId())
                            .emojiChar(emoji.getEmojiChar())
                            .userId(userId)
                            .createdAt(saved.getCreatedAt())
                            .build();
                });
    }

    /**
     * DELETE /api/v1/schedules/tasks/{taskId}/emojis/{emojiId} - 이모지 리액션 삭제
     */
    @Transactional
    public void deleteReaction(Long taskId, Long emojiId, String userId) {
        // 할 일 존재 확인
        taskRepository.findById(taskId)
                .orElseThrow(() -> new CustomException(ErrorCode.S4041));

        // 해당 유저의 모든 동일 리액션 조회 후 삭제 (중복 데이터가 있을 수 있으므로 리스트로 조회)
        List<TaskEmojiData> reactions = taskEmojiRepository.findAllByTask_TaskIdAndEmoji_EmojiIdAndUserId(taskId, emojiId, userId);
        if (reactions.isEmpty()) {
            throw new CustomException(ErrorCode.C4041);
        }
        taskEmojiRepository.deleteAll(reactions);
    }
}
