package com.planit.task;

import com.planit.global.CustomException;
import com.planit.global.ErrorCode;
import com.planit.grpc.UserServiceGrpcClient;
import com.planit.grpc.UserActionLogGrpcClient;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

        private final TaskRepository taskRepository;
        private final WeekGoalRepository weekGoalRepository;
        private final TaskEmojiRepository taskEmojiRepository;
        private final UserServiceGrpcClient userServiceGrpcClient;
        private final UserActionLogGrpcClient actionLogGrpcClient;

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
                // LAZY 로딩 문제 방지: weekGoal → goal → category까지 fetch join
                TaskData task = taskRepository.findByIdWithWeekGoal(taskId)
                                .orElseThrow(() -> new CustomException(ErrorCode.S4041));
                
                // 1️⃣ 메인 로직: 완료 상태 토글
                boolean wasComplete = task.isComplete();
                task.setComplete(!task.isComplete());
                
                // 2️⃣ 비동기 행동 로그 전송 (완료 → 미완료는 로그 안 남김)
                if (!wasComplete && task.isComplete()) {
                        // 완료 처리된 경우에만 로그 전송
                        String userId = extractUserId(task);
                        Long goalsId = extractGoalsId(task);
                        
                        log.info("[TaskService] toggleComplete: taskId={}, userId={}, goalsId={}, hasWeekGoal={}",
                                taskId, userId, goalsId, task.getWeekGoal() != null);
                        
                        if (userId != null) {
                                // 목표 없음 할 일은 goalsId를 0으로 전송
                                Long finalGoalsId = (goalsId != null) ? goalsId : 0L;
                                LocalDateTime actionTime = LocalDateTime.now();
                                
                                log.info("[TaskService] Sending ActionLog: userId={}, taskId={}, goalsId={} (목표 {})",
                                        userId, taskId, finalGoalsId, goalsId != null ? "있음" : "없음");
                                
                                actionLogGrpcClient.recordCompletedAction(
                                        userId,
                                        task.getTaskId(),
                                        finalGoalsId,
                                        task.getTargetDate(),
                                        actionTime
                                );
                        } else {
                                log.error("[TaskService] Skipping ActionLog: userId is null! taskId={}", taskId);
                        }
                }
                
                return CompleteTaskResponse.builder()
                                .taskId(task.getTaskId())
                                .complete(task.isComplete())
                                .updatedAt(task.getUpdatedAt())
                                .build();
        }

        // 6. 할 일 삭제 (Soft Delete)
        @Transactional
        public void deleteTask(Long taskId) {
                // LAZY 로딩 문제 방지: weekGoal → goal → category까지 fetch join
                TaskData task = taskRepository.findByIdWithWeekGoal(taskId)
                                .orElseThrow(() -> new CustomException(ErrorCode.S4041));
                
                // 1️⃣ 비동기 행동 로그 전송 (삭제 전에 데이터 추출)
                String userId = extractUserId(task);
                Long goalsId = extractGoalsId(task);
                
                if (userId != null) {
                        // 목표 없음 할 일은 goalsId를 0으로 전송
                        Long finalGoalsId = (goalsId != null) ? goalsId : 0L;
                        LocalDateTime actionTime = LocalDateTime.now();
                        
                        actionLogGrpcClient.recordDeletedAction(
                                userId,
                                task.getTaskId(),
                                finalGoalsId,
                                task.getTargetDate(),
                                actionTime
                        );
                }
                
                // 2️⃣ 메인 로직: Soft Delete
                taskRepository.deleteById(taskId);
        }

        // 7. 할 일 미루기 (targetDate +1일)
        @Transactional
        public PostponeTaskResponse postponeTask(Long taskId) {
                // LAZY 로딩 문제 방지: weekGoal → goal → category까지 fetch join
                TaskData task = taskRepository.findByIdWithWeekGoal(taskId)
                                .orElseThrow(() -> new CustomException(ErrorCode.S4041));
                
                // 1️⃣ 메인 로직: 날짜 +1일
                LocalDate originalDate = task.getTargetDate();
                LocalDate postponedDate = originalDate.plusDays(1);
                task.setTargetDate(postponedDate);
                
                // 2️⃣ 비동기 행동 로그 전송
                String userId = extractUserId(task);
                Long goalsId = extractGoalsId(task);
                
                if (userId != null) {
                        // 목표 없음 할 일은 goalsId를 0으로 전송
                        Long finalGoalsId = (goalsId != null) ? goalsId : 0L;
                        LocalDateTime actionTime = LocalDateTime.now();
                        
                        actionLogGrpcClient.recordPostponedAction(
                                userId,
                                task.getTaskId(),
                                finalGoalsId,
                                originalDate,
                                postponedDate,
                                actionTime
                        );
                }
                
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

        /**
         * TaskData에서 userId 추출
         * - 목표 있음: weekGoal → goal → category → userId
         * - 목표 없음: task.userId 직접 사용
         */
        private String extractUserId(TaskData task) {
                try {
                        if (task.getWeekGoal() != null) {
                                // 목표 있음: weekGoal → goal → category → userId
                                // LAZY 로딩 강제 실행
                                WeekGoalData weekGoal = task.getWeekGoal();
                                if (weekGoal.getGoal() != null && 
                                    weekGoal.getGoal().getCategory() != null) {
                                        return weekGoal.getGoal().getCategory().getUserId();
                                }
                                log.warn("[TaskService] extractUserId: weekGoal exists but goal/category is null, taskId={}",
                                        task.getTaskId());
                                return null;
                        } else {
                                // 목표 없음: task.userId 직접 사용
                                return task.getUserId();
                        }
                } catch (Exception e) {
                        log.error("[TaskService] extractUserId failed: taskId={}", task.getTaskId(), e);
                        return null;
                }
        }

        /**
         * TaskData에서 goalsId 추출
         * - 목표 있음: weekGoal → goal → goalsId
         * - 목표 없음: null (호출하는 쪽에서 0으로 변환하여 전송)
         */
        private Long extractGoalsId(TaskData task) {
                try {
                        if (task.getWeekGoal() != null) {
                                // LAZY 로딩 강제 실행
                                WeekGoalData weekGoal = task.getWeekGoal();
                                if (weekGoal.getGoal() != null) {
                                        return weekGoal.getGoal().getGoalsId();
                                }
                                log.warn("[TaskService] extractGoalsId: weekGoal exists but goal is null, taskId={}",
                                        task.getTaskId());
                                return null;
                        }
                        return null; // 목표 없음 할 일
                } catch (Exception e) {
                        log.error("[TaskService] extractGoalsId failed: taskId={}", task.getTaskId(), e);
                        return null;
                }
        }
}
