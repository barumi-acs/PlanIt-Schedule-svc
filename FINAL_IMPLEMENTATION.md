# Action Log 최종 구현 요약

## ✅ 수정 완료 사항

### 1. Proto 메시지 경량화 (책임 분리)

**변경 전**:
```protobuf
message ActionLogRequest {
  string user_id = 1;
  int64 task_id = 2;
  int64 goals_id = 3;
  string action_type = 4;
  string action_time = 5;
  string due_date = 6;
  string postponed_to_date = 7;
  string day_of_week = 8;        // ❌ 제거됨
  int32 hour_of_day = 9;         // ❌ 제거됨
}
```

**변경 후** (최종):
```protobuf
message ActionLogRequest {
  string user_id = 1;
  int64 task_id = 2;
  int64 goals_id = 3;
  string action_type = 4;
  string action_time = 5;        // ✅ Insight Service가 이것으로 day_of_week, hour_of_day 계산
  string due_date = 6;
  string postponed_to_date = 7;  // ✅ POSTPONED일 때만 (원래 날짜 +1일)
}
```

**설계 원칙**:
- Schedule Service: 원본 데이터만 전송 (Single Source of Truth)
- Insight Service: 파생 데이터 계산 (day_of_week, hour_of_day)
- 책임 분리로 각 서비스의 역할 명확화

---

## 📝 최종 구현 코드

### 1. action_log_service.proto (최종)

```protobuf
syntax = "proto3";

option java_multiple_files = true;
option java_package = "com.planit.grpc.actionlog";
option java_outer_classname = "ActionLogServiceProto";

package actionlog;

service ActionLogService {
  rpc RecordActionLog(ActionLogRequest) returns (ActionLogResponse);
  rpc RecordActionLogBatch(ActionLogBatchRequest) returns (ActionLogBatchResponse);
}

/**
 * 행동 로그 요청
 * 
 * Schedule Service는 원본 데이터만 전송
 * day_of_week, hour_of_day는 Insight Service가 action_time으로부터 계산
 */
message ActionLogRequest {
  string user_id = 1;           // 사용자 ID
  int64 task_id = 2;            // 할 일 ID
  int64 goals_id = 3;           // 목표 ID (nullable)
  string action_type = 4;       // COMPLETED, POSTPONED, DELETED
  string action_time = 5;       // 행동 시각 (ISO 8601: 2026-03-06T12:30:00)
  string due_date = 6;          // 원래 마감일 (YYYY-MM-DD)
  string postponed_to_date = 7; // 미룬 날짜 (YYYY-MM-DD, POSTPONED일 때만)
}

message ActionLogResponse {
  bool success = 1;
  string message = 2;
  int64 log_id = 3;
}

message ActionLogBatchRequest {
  repeated ActionLogRequest logs = 1;
}

message ActionLogBatchResponse {
  bool success = 1;
  string message = 2;
  int32 recorded_count = 3;
}
```

---

### 2. UserActionLogGrpcClient.java (최종)

```java
package com.planit.grpc;

import com.planit.grpc.actionlog.ActionLogRequest;
import com.planit.grpc.actionlog.ActionLogResponse;
import com.planit.grpc.actionlog.ActionLogServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserActionLogGrpcClient {

    @GrpcClient("insight-service")
    private ActionLogServiceGrpc.ActionLogServiceBlockingStub actionLogStub;

    /**
     * 할 일 완료 행동 로그 기록 (비동기)
     */
    @Async("actionLogExecutor")
    public void recordCompletedAction(
            String userId,
            Long taskId,
            Long goalsId,
            LocalDate dueDate,
            LocalDateTime actionTime) {
        
        try {
            ActionLogRequest request = ActionLogRequest.newBuilder()
                    .setUserId(userId)
                    .setTaskId(taskId)
                    .setGoalsId(goalsId)
                    .setActionType("COMPLETED")
                    .setActionTime(actionTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .setDueDate(dueDate.toString())
                    .setPostponedToDate("")
                    .build();

            ActionLogResponse response = actionLogStub
                    .withDeadlineAfter(3, TimeUnit.SECONDS)
                    .recordActionLog(request);

            log.info("[ActionLog] Successfully recorded COMPLETED: user={}, task={}, goals={}, logId={}",
                    userId, taskId, goalsId, response.getLogId());

        } catch (StatusRuntimeException e) {
            log.error("[ActionLog] Failed to record COMPLETED (gRPC error): user={}, task={}, goals={}, status={}",
                    userId, taskId, goalsId, e.getStatus());
        } catch (Exception e) {
            log.error("[ActionLog] Failed to record COMPLETED (unexpected error): user={}, task={}, goals={}",
                    userId, taskId, goalsId, e);
        }
    }

    /**
     * 할 일 미루기 행동 로그 기록 (비동기)
     */
    @Async("actionLogExecutor")
    public void recordPostponedAction(
            String userId,
            Long taskId,
            Long goalsId,
            LocalDate originalDueDate,
            LocalDate postponedToDate,
            LocalDateTime actionTime) {
        
        try {
            ActionLogRequest request = ActionLogRequest.newBuilder()
                    .setUserId(userId)
                    .setTaskId(taskId)
                    .setGoalsId(goalsId)
                    .setActionType("POSTPONED")
                    .setActionTime(actionTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .setDueDate(originalDueDate.toString())
                    .setPostponedToDate(postponedToDate.toString())
                    .build();

            ActionLogResponse response = actionLogStub
                    .withDeadlineAfter(3, TimeUnit.SECONDS)
                    .recordActionLog(request);

            log.info("[ActionLog] Successfully recorded POSTPONED: user={}, task={}, goals={}, from={}, to={}, logId={}",
                    userId, taskId, goalsId, originalDueDate, postponedToDate, response.getLogId());

        } catch (StatusRuntimeException e) {
            log.error("[ActionLog] Failed to record POSTPONED (gRPC error): user={}, task={}, goals={}, status={}",
                    userId, taskId, goalsId, e.getStatus());
        } catch (Exception e) {
            log.error("[ActionLog] Failed to record POSTPONED (unexpected error): user={}, task={}, goals={}",
                    userId, taskId, goalsId, e);
        }
    }

    /**
     * 할 일 삭제 행동 로그 기록 (비동기)
     */
    @Async("actionLogExecutor")
    public void recordDeletedAction(
            String userId,
            Long taskId,
            Long goalsId,
            LocalDate dueDate,
            LocalDateTime actionTime) {
        
        try {
            ActionLogRequest request = ActionLogRequest.newBuilder()
                    .setUserId(userId)
                    .setTaskId(taskId)
                    .setGoalsId(goalsId)
                    .setActionType("DELETED")
                    .setActionTime(actionTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .setDueDate(dueDate.toString())
                    .setPostponedToDate("")
                    .build();

            ActionLogResponse response = actionLogStub
                    .withDeadlineAfter(3, TimeUnit.SECONDS)
                    .recordActionLog(request);

            log.info("[ActionLog] Successfully recorded DELETED: user={}, task={}, goals={}, logId={}",
                    userId, taskId, goalsId, response.getLogId());

        } catch (StatusRuntimeException e) {
            log.error("[ActionLog] Failed to record DELETED (gRPC error): user={}, task={}, goals={}, status={}",
                    userId, taskId, goalsId, e.getStatus());
        } catch (Exception e) {
            log.error("[ActionLog] Failed to record DELETED (unexpected error): user={}, task={}, goals={}",
                    userId, taskId, goalsId, e);
        }
    }
}
```

---

### 3. TaskService.java 핵심 로직 (최종)

#### 3-1. 할 일 완료 토글

```java
@Transactional
public CompleteTaskResponse toggleComplete(Long taskId) {
    TaskData task = taskRepository.findById(taskId)
            .orElseThrow(() -> new CustomException(ErrorCode.S4041));
    
    // 1️⃣ 메인 로직: 완료 상태 토글
    boolean wasComplete = task.isComplete();
    task.setComplete(!task.isComplete());
    
    // 2️⃣ 비동기 행동 로그 전송 (완료 → 미완료는 로그 안 남김)
    if (!wasComplete && task.isComplete()) {
        String userId = extractUserId(task);
        Long goalsId = extractGoalsId(task);
        
        if (userId != null && goalsId != null) {
            LocalDateTime actionTime = LocalDateTime.now();
            actionLogGrpcClient.recordCompletedAction(
                userId,
                task.getTaskId(),
                goalsId,
                task.getTargetDate(),  // due_date
                actionTime             // action_time (Insight가 day_of_week, hour_of_day 계산)
            );
        }
    }
    
    return CompleteTaskResponse.builder()
            .taskId(task.getTaskId())
            .complete(task.isComplete())
            .updatedAt(task.getUpdatedAt())
            .build();
}
```

#### 3-2. 할 일 미루기 (+1일 고정)

```java
@Transactional
public PostponeTaskResponse postponeTask(Long taskId) {
    TaskData task = taskRepository.findById(taskId)
            .orElseThrow(() -> new CustomException(ErrorCode.S4041));
    
    // 1️⃣ 메인 로직: 날짜 +1일 (비즈니스 룰: 무조건 다음 날로 미루기)
    LocalDate originalDate = task.getTargetDate();
    LocalDate postponedDate = originalDate.plusDays(1);
    task.setTargetDate(postponedDate);
    
    // 2️⃣ 비동기 행동 로그 전송
    String userId = extractUserId(task);
    Long goalsId = extractGoalsId(task);
    
    if (userId != null && goalsId != null) {
        LocalDateTime actionTime = LocalDateTime.now();
        actionLogGrpcClient.recordPostponedAction(
            userId,
            task.getTaskId(),
            goalsId,
            originalDate,      // due_date (원래 마감일)
            postponedDate,     // postponed_to_date (원래 마감일 +1일)
            actionTime         // action_time
        );
    }
    
    return PostponeTaskResponse.builder()
            .taskId(task.getTaskId())
            .content(task.getContent())
            .targetDate(task.getTargetDate())
            .updatedAt(task.getUpdatedAt())
            .build();
}
```

#### 3-3. 할 일 삭제

```java
@Transactional
public void deleteTask(Long taskId) {
    TaskData task = taskRepository.findById(taskId)
            .orElseThrow(() -> new CustomException(ErrorCode.S4041));
    
    // 1️⃣ 비동기 행동 로그 전송 (삭제 전에 데이터 추출)
    String userId = extractUserId(task);
    Long goalsId = extractGoalsId(task);
    
    if (userId != null && goalsId != null) {
        LocalDateTime actionTime = LocalDateTime.now();
        actionLogGrpcClient.recordDeletedAction(
            userId,
            task.getTaskId(),
            goalsId,
            task.getTargetDate(),
            actionTime
        );
    }
    
    // 2️⃣ 메인 로직: Soft Delete
    taskRepository.deleteById(taskId);
}
```

---

## 🎯 핵심 설계 원칙

### 1. 책임 분리 (Separation of Concerns)

| 서비스 | 책임 |
|--------|------|
| Schedule Service | 원본 데이터 전송 (user_id, task_id, goals_id, action_time, due_date, postponed_to_date) |
| Insight Service | 파생 데이터 계산 (day_of_week, hour_of_day) |

### 2. 비즈니스 룰

- **미루기 정책**: 무조건 다음 날(+1일)로 고정
- **완료 토글**: 완료 → 미완료는 로그 안 남김 (완료 처리만 기록)
- **목표 없음 할 일**: goalsId가 null이면 행동 로그 전송 안 함

### 3. 데이터 흐름

```
[프론트엔드]
    PATCH /tasks/123/postpone
         ↓
[TaskController]
    postponeTask(123)
         ↓
[TaskService]
    @Transactional 시작
         ↓
    taskRepository.findById(123)
         ↓
    originalDate = task.getTargetDate()  // 2026-03-06
    postponedDate = originalDate.plusDays(1)  // 2026-03-07
    task.setTargetDate(postponedDate)
         ↓
    @Transactional 커밋 ✅
         ↓
    extractUserId(task) → "user123"
    extractGoalsId(task) → 456
    actionTime = LocalDateTime.now() → 2026-03-06T12:30:00
         ↓
    actionLogGrpcClient.recordPostponedAction(
        "user123", 123, 456,
        "2026-03-06",  // due_date
        "2026-03-07",  // postponed_to_date
        "2026-03-06T12:30:00"  // action_time
    )
         ↓ @Async (별도 스레드)
         ↓
[Insight Service]
    ActionLogServiceImpl.recordActionLog()
         ↓
    action_time 파싱: 2026-03-06T12:30:00
    day_of_week 계산: WEDNESDAY
    hour_of_day 계산: 12
         ↓
    user_action_logs 테이블 INSERT
```

---

## ✅ 최종 체크리스트

### Schedule Service
- [x] Proto 파일 경량화 (day_of_week, hour_of_day 제거)
- [x] UserActionLogGrpcClient 수정 (파생 데이터 계산 로직 제거)
- [x] TaskService 구현 확인 (postponedDate = originalDate.plusDays(1))
- [x] 비동기 처리 (@Async)
- [x] 장애 격리 (try-catch)

### Insight Service (다음 단계)
- [ ] Proto 파일 복사
- [ ] Entity 생성 (day_of_week, hour_of_day 컬럼 포함)
- [ ] gRPC Service 구현 (action_time으로 day_of_week, hour_of_day 계산)
- [ ] 통합 테스트

---

## 🚀 다음 단계

1. Proto 재컴파일
```bash
cd PlanIt-Schedule-svc
./gradlew clean build -x test
```

2. Insight Service 구현
- `action_time` 파싱하여 `day_of_week`, `hour_of_day` 계산
- `user_action_logs` 테이블에 INSERT

3. 통합 테스트
- Schedule Service 실행
- Insight Service 실행
- API 호출 후 DB 확인

---

**작성일**: 2026-03-06  
**최종 수정**: 책임 분리 원칙 적용 (day_of_week, hour_of_day 제거)  
**버전**: 2.0 (Final)
