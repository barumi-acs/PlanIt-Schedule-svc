package com.planit.task;

import com.planit.global.CustomException;
import com.planit.global.ErrorCode;
import com.planit.grpc.UserServiceGrpcClient;
import com.planit.task.dto.CreateTaskRequest;
import com.planit.task.dto.DailyTaskItem;
import com.planit.task.dto.DailyTaskResponse;
import com.planit.task.dto.EmojiItem;
import com.planit.task.dto.FriendTaskItem;
import com.planit.task.dto.FriendTaskResponse;
import com.planit.task.dto.TaskResponse;
import com.planit.task.emoji.TaskEmojiData;
import com.planit.task.emoji.TaskEmojiRepository;
import com.planit.weekgoal.WeekGoalRepository;
import com.planit.weekgoal.WeekGoalData;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final WeekGoalRepository weekGoalRepository;
    private final TaskEmojiRepository taskEmojiRepository;
    private final UserServiceGrpcClient userServiceGrpcClient;

    // 1. 할 일 등록
    @Transactional
    public TaskResponse createTask(CreateTaskRequest req) {
        // 필드 유효성 검사
        if (req.getWeekGoalsId() == null || req.getContent() == null || req.getContent().isBlank()
                || req.getTargetDate() == null) {
            throw new CustomException(ErrorCode.C4001);
        }

        // 날짜 파싱
        LocalDate targetDate;
        try {
            targetDate = LocalDate.parse(req.getTargetDate());
        } catch (DateTimeParseException e) {
            throw new CustomException(ErrorCode.C4001);
        }

        // 주간 목표 존재 여부 확인
        WeekGoalData weekGoal = weekGoalRepository.findById(req.getWeekGoalsId())
                .orElseThrow(() -> new CustomException(ErrorCode.C4001));

        TaskData task = new TaskData();
        task.setWeekGoal(weekGoal);
        task.setContent(req.getContent());
        task.setTargetDate(targetDate);
        TaskData saved = taskRepository.save(task);

        return toResponse(saved);
    }

    // 2. 일간 할 일 조회
    @Transactional(readOnly = true)
    public DailyTaskResponse getDailyTasks(String myUserId, String targetDateStr) {
        if (myUserId == null || myUserId.isBlank()) {
            throw new CustomException(ErrorCode.C4001);
        }

        // targetDate 없으면 오늘 날짜 사용
        LocalDate targetDate;
        try {
            targetDate = (targetDateStr == null || targetDateStr.isBlank())
                    ? LocalDate.now()
                    : LocalDate.parse(targetDateStr);
        } catch (DateTimeParseException e) {
            throw new CustomException(ErrorCode.C4001);
        }

        List<TaskData> tasks = taskRepository.findByUserIdAndTargetDate(myUserId, targetDate);

        int totalCount = tasks.size();
        int completedCount = (int) tasks.stream().filter(TaskData::isComplete).count();
        int progressRate = totalCount == 0 ? 0 : (completedCount * 100 / totalCount);

        // @Transactional 안에서 LAZY 로딩 → 1차 캐시로 동일 weekGoal은 1번만 조회
        List<DailyTaskItem> items = tasks.stream()
                .map(t -> DailyTaskItem.builder()
                        .taskId(t.getTaskId())
                        .weekGoalsId(t.getWeekGoal().getWeekGoalsId())
                        .weekGoalsTitle(t.getWeekGoal().getTitle())
                        .content(t.getContent())
                        .complete(t.isComplete())
                        .targetDate(t.getTargetDate())
                        .build())
                .collect(Collectors.toList());

        return DailyTaskResponse.builder()
                .targetDate(targetDate)
                .totalCount(totalCount)
                .completedCount(completedCount)
                .progressRate(progressRate)
                .tasks(items)
                .build();
    }

    // 3. 친구의 할 일 조회
    @Transactional(readOnly = true)
    public FriendTaskResponse getFriendTasks(String myUserId, String friendUserId, String targetDateStr) {
        if (myUserId == null || myUserId.isBlank() || friendUserId == null || friendUserId.isBlank()) {
            throw new CustomException(ErrorCode.C4001);
        }

        // targetDate 없으면 오늘 날짜 사용
        LocalDate targetDate;
        try {
            targetDate = (targetDateStr == null || targetDateStr.isBlank())
                    ? LocalDate.now()
                    : LocalDate.parse(targetDateStr);
        } catch (DateTimeParseException e) {
            throw new CustomException(ErrorCode.C4001);
        }

        // 친구 관계 확인 (gRPC → User Service, 현재는 stub)
        boolean isFriend = userServiceGrpcClient.checkFriendship(myUserId, friendUserId);
        if (!isFriend) {
            throw new CustomException(ErrorCode.S4031);
        }

        // 친구의 할 일 조회
        List<TaskData> tasks = taskRepository.findByUserIdAndTargetDate(friendUserId, targetDate);

        if (tasks.isEmpty()) {
            return FriendTaskResponse.builder()
                    .friendUserId(friendUserId)
                    .targetDate(targetDate)
                    .tasks(Collections.emptyList())
                    .build();
        }

        // 이모지 일괄 조회 (N+1 방지)
        List<Long> taskIds = tasks.stream().map(TaskData::getTaskId).collect(Collectors.toList());
        List<TaskEmojiData> allEmojis = taskEmojiRepository.findByTask_TaskIdIn(taskIds);

        // taskId → (emojiId → 이모지 리스트) 구조로 그룹
        Map<Long, Map<Long, List<TaskEmojiData>>> emojisByTaskAndId = allEmojis.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getTask().getTaskId(),
                        Collectors.groupingBy(e -> e.getEmoji().getEmojiId())));

        List<FriendTaskItem> items = tasks.stream()
                .map(t -> {
                    Map<Long, List<TaskEmojiData>> byId = emojisByTaskAndId.getOrDefault(t.getTaskId(),
                            Collections.emptyMap());

                    List<EmojiItem> emojis = byId.entrySet().stream()
                            .map(entry -> {
                                Long emojiId = entry.getKey();
                                List<TaskEmojiData> reactors = entry.getValue();
                                return EmojiItem.builder()
                                        .emojiId(String.valueOf(emojiId))
                                        .count(reactors.size())
                                        .myReaction(reactors.stream()
                                                .anyMatch(e -> myUserId.equals(e.getUserId())))
                                        .build();
                            })
                            .collect(Collectors.toList());

                    return FriendTaskItem.builder()
                            .taskId(t.getTaskId())
                            .content(t.getContent())
                            .complete(t.isComplete())
                            .targetDate(t.getTargetDate())
                            .emojis(emojis)
                            .build();
                })
                .collect(Collectors.toList());

        return FriendTaskResponse.builder()
                .friendUserId(friendUserId)
                .targetDate(targetDate)
                .tasks(items)
                .build();
    }

    // TaskData → TaskResponse 변환
    private TaskResponse toResponse(TaskData t) {
        return TaskResponse.builder()
                .taskId(t.getTaskId())
                .weekGoalsId(t.getWeekGoal().getWeekGoalsId())
                .content(t.getContent())
                .complete(t.isComplete())
                .targetDate(t.getTargetDate())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
