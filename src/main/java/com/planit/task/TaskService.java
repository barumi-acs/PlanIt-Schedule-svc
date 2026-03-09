package com.planit.task;

import com.planit.global.CustomException;
import com.planit.global.ErrorCode;
import com.planit.grpc.UserServiceGrpcClient;
import com.planit.task.dto.CompleteTaskResponse;
import com.planit.task.dto.CreateTaskRequest;
import com.planit.task.dto.DailyTaskItem;
import com.planit.task.dto.DailyTaskResponse;
import com.planit.task.dto.EmojiItem;
import com.planit.task.dto.FriendTaskItem;
import com.planit.task.dto.FriendTaskResponse;
import com.planit.task.dto.PostponeTaskResponse;
import com.planit.task.dto.TaskResponse;
import com.planit.task.dto.UpdateTaskRequest;
import com.planit.task.dto.UpdateTaskResponse;
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
                if (req.getContent() == null || req.getContent().isBlank() || req.getTargetDate() == null) {
                        throw new CustomException(ErrorCode.C4001);
                }

                // 날짜 파싱
                LocalDate targetDate;
                try {
                        targetDate = LocalDate.parse(req.getTargetDate());
                } catch (DateTimeParseException e) {
                        throw new CustomException(ErrorCode.C4001);
                }

                TaskData task = new TaskData();
                task.setContent(req.getContent());
                task.setTargetDate(targetDate);

                if (req.getWeekGoalsId() != null) {
                        // 목표 있음: weekGoal FK 설정
                        WeekGoalData weekGoal = weekGoalRepository.findById(req.getWeekGoalsId())
                                        .orElseThrow(() -> new CustomException(ErrorCode.C4001));
                        task.setWeekGoal(weekGoal);
                        task.setCategory(weekGoal.getTitle()); // 주간 목표 제목을 카테고리로 저장
                } else {
                        // 목표 없음: userId 직접 저장
                        if (req.getUserId() == null || req.getUserId().isBlank()) {
                                throw new CustomException(ErrorCode.C4001);
                        }
                        task.setUserId(req.getUserId());
                        task.setCategory(req.getCategory()); // FE에서 전달한 카테고리 저장
                }

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
                // weekGoal이 null인 경우(목표 없음 할 일)도 안전하게 처리
                List<DailyTaskItem> items = tasks.stream()
                                .map(t -> DailyTaskItem.builder()
                                                .taskId(t.getTaskId())
                                                .weekGoalsId(t.getWeekGoal() != null ? t.getWeekGoal().getWeekGoalsId()
                                                                : null)
                                                .weekGoalsTitle(t.getWeekGoal() != null ? t.getWeekGoal().getTitle()
                                                                : null)
                                                .category(t.getCategory())
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
                                        Map<Long, List<TaskEmojiData>> byId = emojisByTaskAndId.getOrDefault(
                                                        t.getTaskId(),
                                                        Collections.emptyMap());

                                        List<EmojiItem> emojis = byId.entrySet().stream()
                                                        .map(entry -> {
                                                                Long emojiId = entry.getKey();
                                                                List<TaskEmojiData> reactors = entry.getValue();
                                                                return EmojiItem.builder()
                                                                                .emojiId(String.valueOf(emojiId))
                                                                                .count(reactors.size())
                                                                                .myReaction(reactors.stream()
                                                                                                .anyMatch(e -> myUserId
                                                                                                                .equals(e.getUserId())))
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

        // 4. 할 일 수정
        @Transactional
        public UpdateTaskResponse updateTask(Long taskId, UpdateTaskRequest req) {
                if (req.getContent() == null || req.getContent().isBlank()) {
                        throw new CustomException(ErrorCode.C4001);
                }
                TaskData task = taskRepository.findById(taskId)
                                .orElseThrow(() -> new CustomException(ErrorCode.S4041));
                task.setContent(req.getContent());
                return UpdateTaskResponse.builder()
                                .taskId(task.getTaskId())
                                .content(task.getContent())
                                .updatedAt(task.getUpdatedAt())
                                .build();
        }

        // 5. 할 일 완료 토글
        @Transactional
        public CompleteTaskResponse toggleComplete(Long taskId) {
                TaskData task = taskRepository.findById(taskId)
                                .orElseThrow(() -> new CustomException(ErrorCode.S4041));
                task.setComplete(!task.isComplete());
                return CompleteTaskResponse.builder()
                                .taskId(task.getTaskId())
                                .complete(task.isComplete())
                                .updatedAt(task.getUpdatedAt())
                                .build();
        }

        // 6. 할 일 삭제 (Soft Delete)
        @Transactional
        public void deleteTask(Long taskId) {
                taskRepository.findById(taskId)
                                .orElseThrow(() -> new CustomException(ErrorCode.S4041));
                taskRepository.deleteById(taskId);
        }

        // 7. 할 일 미루기 (targetDate +1일)
        @Transactional
        public PostponeTaskResponse postponeTask(Long taskId) {
                TaskData task = taskRepository.findById(taskId)
                                .orElseThrow(() -> new CustomException(ErrorCode.S4041));
                task.setTargetDate(task.getTargetDate().plusDays(1));
                return PostponeTaskResponse.builder()
                                .taskId(task.getTaskId())
                                .content(task.getContent())
                                .targetDate(task.getTargetDate())
                                .updatedAt(task.getUpdatedAt())
                                .build();
        }

        // TaskData → TaskResponse 변환
        private TaskResponse toResponse(TaskData t) {
                return TaskResponse.builder()
                                .taskId(t.getTaskId())
                                .weekGoalsId(t.getWeekGoal() != null ? t.getWeekGoal().getWeekGoalsId() : null)
                                .content(t.getContent())
                                .complete(t.isComplete())
                                .targetDate(t.getTargetDate())
                                .createdAt(t.getCreatedAt())
                                .updatedAt(t.getUpdatedAt())
                                .build();
        }
}
