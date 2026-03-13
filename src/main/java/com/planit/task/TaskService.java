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
import com.planit.category.CategoryRepository;
import com.planit.category.category_list.CategoryListRepository;

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
        private final CategoryRepository categoryRepository;
        private final CategoryListRepository categoryListRepository;
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

                // 유저 식별자 확인
                String userId = req.getUserId();
                if (userId == null || userId.isBlank()) {
                        throw new CustomException(ErrorCode.C4001);
                }

                if (req.getWeekGoalsId() != null) {
                        // 목표 있음: weekGoal FK 설정
                        WeekGoalData weekGoal = weekGoalRepository.findById(req.getWeekGoalsId())
                                        .orElseThrow(() -> new CustomException(ErrorCode.C4001));
                        task.setWeekGoal(weekGoal);
                        // 목표의 카테고리 그대로 사용
                        task.setCategory(weekGoal.getGoal().getCategory());
                } else {
                        // 목표 없음: 전달된 카테고리 이름으로 CategoryData 조회/생성 필요
                        String categoryName = req.getCategory() != null ? req.getCategory() : "기타";
                        com.planit.category.CategoryData category = getOrCreateCategory(userId, categoryName);
                        task.setCategory(category);
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

                List<DailyTaskItem> items = tasks.stream()
                                .map(t -> {
                                        String catName = (t.getCategory() != null && t.getCategory().getCategoryList() != null)
                                                        ? t.getCategory().getCategoryList().getName() : "기타";
                                        
                                        String gTitle = (t.getWeekGoal() != null && t.getWeekGoal().getGoal() != null)
                                                        ? t.getWeekGoal().getGoal().getTitle() : null;

                                        return DailyTaskItem.builder()
                                                        .taskId(t.getTaskId())
                                                        .weekGoalsId(t.getWeekGoal() != null ? t.getWeekGoal().getWeekGoalsId() : null)
                                                        .weekGoalsTitle(t.getWeekGoal() != null ? t.getWeekGoal().getTitle() : null)
                                                        .goalTitle(gTitle)
                                                        .category(catName)
                                                        .content(t.getContent())
                                                        .complete(t.isComplete())
                                                        .targetDate(t.getTargetDate())
                                                        .build();
                                })
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

                                        String catName = (t.getCategory() != null && t.getCategory().getCategoryList() != null)
                                                        ? t.getCategory().getCategoryList().getName() : "기타";
                                        
                                        String gTitle = (t.getWeekGoal() != null && t.getWeekGoal().getGoal() != null)
                                                        ? t.getWeekGoal().getGoal().getTitle() : null;

                                        return FriendTaskItem.builder()
                                                        .taskId(t.getTaskId())
                                                        .content(t.getContent())
                                                        .complete(t.isComplete())
                                                        .targetDate(t.getTargetDate())
                                                        .category(catName)
                                                        .weekGoalsId(t.getWeekGoal() != null ? t.getWeekGoal().getWeekGoalsId() : null)
                                                        .weekGoalsTitle(t.getWeekGoal() != null ? t.getWeekGoal().getTitle() : null)
                                                        .goalTitle(gTitle)
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
                String categoryName = (t.getCategory() != null && t.getCategory().getCategoryList() != null)
                        ? t.getCategory().getCategoryList().getName() : "기타";
                
                // 🔍 디버깅 로그 추가
                log.info("Task 조회 응답 categoryName - taskId: {}, categoryName: {}", 
                        t.getTaskId(), categoryName);
                
                return TaskResponse.builder()
                                .taskId(t.getTaskId())
                                .weekGoalsId(t.getWeekGoal() != null ? t.getWeekGoal().getWeekGoalsId() : null)
                                .content(t.getContent())
                                .complete(t.isComplete())
                                .category(categoryName)
                                .targetDate(t.getTargetDate())
                                .createdAt(t.getCreatedAt())
                                .updatedAt(t.getUpdatedAt())
                                .build();
        }

        /**
         * 유저의 카테고리를 조회하거나 없으면 새로 생성
         */
        private com.planit.category.CategoryData getOrCreateCategory(String userId, String categoryName) {
                com.planit.category.category_list.CategoryList categoryList = categoryListRepository.findByName(categoryName)
                                .orElseGet(() -> {
                                        log.warn("⚠️ 카테고리 '{}'가 존재하지 않아 '기타'를 사용합니다.", categoryName);
                                        return categoryListRepository.findByName("기타")
                                                        .orElseThrow(() -> new CustomException(ErrorCode.C4041));
                                });

                return categoryRepository.findByUserIdAndCategoryList_ListId(userId, categoryList.getListId())
                                .orElseGet(() -> {
                                        log.info("📂 유저({})의 새로운 카테고리 '{}' 생성", userId, categoryName);
                                        com.planit.category.CategoryData newCategory = new com.planit.category.CategoryData();
                                        newCategory.setUserId(userId);
                                        newCategory.setCategoryList(categoryList);
                                        return categoryRepository.save(newCategory);
                                });
        }

        /**
         * TaskData에서 userId 추출
         */
        private String extractUserId(TaskData task) {
                try {
                        return task.getCategory().getUserId();
                } catch (Exception e) {
                        log.error("[TaskService] extractUserId failed: taskId={}", task.getTaskId(), e);
                        return null;
                }
        }

        /**
         * TaskData에서 goalsId 추출
         */
        private Long extractGoalsId(TaskData task) {
                try {
                        if (task.getWeekGoal() != null) {
                                WeekGoalData weekGoal = task.getWeekGoal();
                                if (weekGoal.getGoal() != null) {
                                        return weekGoal.getGoal().getGoalsId();
                                }
                                return null;
                        }
                        return null;
                } catch (Exception e) {
                        log.error("[TaskService] extractGoalsId failed: taskId={}", task.getTaskId(), e);
                        return null;
                }
        }
}
