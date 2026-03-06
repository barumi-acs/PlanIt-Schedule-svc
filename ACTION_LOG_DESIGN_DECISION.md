# Action Log 데이터 수집 설계 결정서

## 📋 요구사항

Schedule Service의 할 일 완료/미루기 API 호출 시, Insight Service의 `user_action_logs` 테이블에 행동 로그를 gRPC로 전송해야 함.

### Insight Service 타겟 테이블 구조
```sql
CREATE TABLE user_action_logs (
    log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    task_id BIGINT NOT NULL,
    goals_id BIGINT NOT NULL,
    action_type VARCHAR(20) NOT NULL,  -- COMPLETED, POSTPONED, DELETED
    action_time DATETIME NOT NULL,
    due_date DATE NOT NULL,
    postponed_to_date DATE,
    day_of_week VARCHAR(10),
    hour_of_day INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 대상 API
1. `PATCH /api/v1/schedules/tasks/{taskId}/complete` - 할 일 완료
2. `PATCH /api/v1/schedules/tasks/{taskId}/postpone` - 할 일 미루기

---

## 🎯 1. 데이터 수집 전략: Backend 조회 방식 (권장)

### 결론: **Backend에서 자체 DB 조회**

**이유**:
1. **보안**: 프론트엔드에서 `goals_id` 같은 민감한 내부 ID를 노출하지 않음
2. **무결성**: 클라이언트가 조작한 데이터가 아닌, 서버의 신뢰할 수 있는 데이터 사용
3. **단순성**: 프론트엔드는 기존 API 명세 그대로 사용 (변경 불필요)
4. **일관성**: 이미 TaskService에서 `extractUserId()`, `extractGoalsId()` 헬퍼 메서드 구현됨
5. **성능**: `taskId`로 조회 시 이미 로드된 Entity를 재사용 (1차 캐시)

### 데이터 수집 흐름
```
1. 프론트엔드: PATCH /tasks/{taskId}/complete
   ↓
2. TaskController: taskId만 받음
   ↓
3. TaskService: taskRepository.findById(taskId)
   ↓
4. TaskData 로드 (LAZY 로딩으로 weekGoal, goal, category 체인 접근 가능)
   ↓
5. 필요한 데이터 추출:
   - userId: extractUserId(task)
   - goalsId: extractGoalsId(task)
   - dueDate: task.getTargetDate()
   - actionTime: LocalDateTime.now()
   - dayOfWeek: dueDate.getDayOfWeek()
   - hourOfDay: actionTime.getHour()
   ↓
6. UserActionLogGrpcClient.recordCompletedAction(...) 비동기 호출
```

### 장점
- ✅ 프론트엔드 변경 불필요 (기존 API 명세 유지)
- ✅ 데이터 무결성 보장 (서버 DB가 Single Source of Truth)
- ✅ 보안 강화 (내부 ID 노출 방지)
- ✅ 성능 최적화 (JPA 1차 캐시 활용)

### 단점
- ⚠️ DB 조회 1회 추가 (하지만 이미 메인 로직에서 조회하므로 추가 비용 없음)

---

## 📝 2. 프론트엔드 영향도 분석: 변경 불필요

### 결론: **API 명세 변경 없음**

기존 API 명세를 그대로 유지하므로 프론트엔드 수정이 필요 없습니다.

### 2.1 할 일 완료 API (변경 없음)

**기존 명세 (유지)**:
```http
PATCH /api/v1/schedules/tasks/{taskId}/complete
```

**Request**: Body 없음 (taskId만 Path Parameter)

**Response**:
```json
{
  "success": true,
  "data": {
    "taskId": 123,
    "complete": true,
    "updatedAt": "2026-03-06T12:30:00"
  }
}
```

**프론트엔드 코드 (변경 없음)**:
```typescript
// 기존 코드 그대로 사용
const completeTask = async (taskId: number) => {
  const response = await fetch(`/api/v1/schedules/tasks/${taskId}/complete`, {
    method: 'PATCH'
  });
  return response.json();
};
```

### 2.2 할 일 미루기 API (변경 없음)

**기존 명세 (유지)**:
```http
PATCH /api/v1/schedules/tasks/{taskId}/postpone
```

**Request**: Body 없음 (taskId만 Path Parameter)

**Response**:
```json
{
  "success": true,
  "data": {
    "taskId": 123,
    "content": "운동하기",
    "targetDate": "2026-03-07",
    "updatedAt": "2026-03-06T12:30:00"
  }
}
```

**프론트엔드 코드 (변경 없음)**:
```typescript
// 기존 코드 그대로 사용
const postponeTask = async (taskId: number) => {
  const response = await fetch(`/api/v1/schedules/tasks/${taskId}/postpone`, {
    method: 'PATCH'
  });
  return response.json();
};
```

### 2.3 프론트엔드 개발자 공지사항

```
📢 프론트엔드 개발자님께

할 일 완료/미루기 API에 백엔드 내부 로직이 추가되었습니다.
(사용자 행동 로그를 분석 서비스로 전송)

✅ API 명세 변경 없음
✅ Request/Response 구조 동일
✅ 프론트엔드 코드 수정 불필요

단, 응답 시간이 약간 증가할 수 있습니다 (비동기 처리로 최소화).
```

---

## 🔧 3. Schedule 백엔드 구현 흐름

### 3.1 UserActionLogGrpcClient 메서드 추가

기존 클라이언트에 `action_time`, `day_of_week`, `hour_of_day` 파라미터를 추가합니다.

```java
package com.planit.grpc;

import com.planit.analytics.grpc.ActionLogRequest;
import com.planit.analytics.grpc.ActionLogResponse;
import com.planit.analytics.grpc.ActionLogServiceGrpc;
import io.grpc.ManagedChannel;
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
                    .setDayOfWeek(dueDate.getDayOfWeek().toString())
                    .setHourOfDay(actionTime.getHour())
                    .build();

            ActionLogResponse response = actionLogStub
                    .withDeadlineAfter(3, TimeUnit.SECONDS)
                    .recordActionLog(request);

            log.info("Successfully recorded COMPLETED action: user={}, task={}, goals={}, logId={}",
                    userId, taskId, goalsId, response.getLogId());

        } catch (StatusRuntimeException e) {
            log.error("Failed to record COMPLETED action for user={}, task={}, goals={}: {}",
                    userId, taskId, goalsId, e.getStatus(), e);
        } catch (Exception e) {
            log.error("Unexpected error recording COMPLETED action for user={}, task={}, goals={}",
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
                    .setDayOfWeek(originalDueDate.getDayOfWeek().toString())
                    .setHourOfDay(actionTime.getHour())
                    .build();

            ActionLogResponse response = actionLogStub
                    .withDeadlineAfter(3, TimeUnit.SECONDS)
                    .recordActionLog(request);

            log.info("Successfully recorded POSTPONED action: user={}, task={}, goals={}, logId={}",
                    userId, taskId, goalsId, response.getLogId());

        } catch (StatusRuntimeException e) {
            log.error("Failed to record POSTPONED action for user={}, task={}, goals={}: {}",
                    userId, taskId, goalsId, e.getStatus(), e);
        } catch (Exception e) {
            log.error("Unexpected error recording POSTPONED action for user={}, task={}, goals={}",
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
                    .setDayOfWeek(dueDate.getDayOfWeek().toString())
                    .setHourOfDay(actionTime.getHour())
                    .build();

            ActionLogResponse response = actionLogStub
                    .withDeadlineAfter(3, TimeUnit.SECONDS)
                    .recordActionLog(request);

            log.info("Successfully recorded DELETED action: user={}, task={}, goals={}, logId={}",
                    userId, taskId, goalsId, response.getLogId());

        } catch (StatusRuntimeException e) {
            log.error("Failed to record DELETED action for user={}, task={}, goals={}: {}",
                    userId, taskId, goalsId, e.getStatus(), e);
        } catch (Exception e) {
            log.error("Unexpected error recording DELETED action for user={}, task={}, goals={}",
                    userId, taskId, goalsId, e);
        }
    }
}
```

### 3.2 Proto 파일 업데이트

```protobuf
message ActionLogRequest {
  string user_id = 1;
  int64 task_id = 2;
  int64 goals_id = 3;
  string action_type = 4;
  string action_time = 5;        // ISO 8601 format: 2026-03-06T12:30:00
  string due_date = 6;            // YYYY-MM-DD
  string postponed_to_date = 7;   // YYYY-MM-DD (POSTPONED만)
  string day_of_week = 8;         // MONDAY, TUESDAY, ...
  int32 hour_of_day = 9;          // 0-23
}
```

### 3.3 TaskService 업데이트

```java
// 5. 할 일 완료 토글
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
                task.getTargetDate(),
                actionTime
            );
        }
    }
    
    return CompleteTaskResponse.builder()
            .taskId(task.getTaskId())
            .complete(task.isComplete())
            .updatedAt(task.getUpdatedAt())
            .build();
}

// 7. 할 일 미루기 (targetDate +1일)
@Transactional
public PostponeTaskResponse postponeTask(Long taskId) {
    TaskData task = taskRepository.findById(taskId)
            .orElseThrow(() -> new CustomException(ErrorCode.S4041));
    
    // 1️⃣ 메인 로직: 날짜 +1일
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

// 6. 할 일 삭제 (Soft Delete)
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

## 📊 데이터 흐름 다이어그램

```
[프론트엔드]
    PATCH /tasks/123/complete
         ↓
[TaskController]
    toggleComplete(123)
         ↓
[TaskService]
    @Transactional 시작
         ↓
    taskRepository.findById(123)
         ↓
    TaskData 로드 (weekGoal, goal, category LAZY 로딩 가능)
         ↓
    task.setComplete(true) → DB UPDATE
         ↓
    @Transactional 커밋 ✅
         ↓
    extractUserId(task) → "user123"
    extractGoalsId(task) → 456
    task.getTargetDate() → 2026-03-06
    LocalDateTime.now() → 2026-03-06T12:30:00
         ↓
    actionLogGrpcClient.recordCompletedAction(...)
         ↓ @Async (별도 스레드)
         ↓
[UserActionLogGrpcClient]
    ActionLogRequest 빌드:
    - userId: "user123"
    - taskId: 123
    - goalsId: 456
    - actionType: "COMPLETED"
    - actionTime: "2026-03-06T12:30:00"
    - dueDate: "2026-03-06"
    - dayOfWeek: "WEDNESDAY"
    - hourOfDay: 12
         ↓
    gRPC 호출 (3초 타임아웃)
         ↓
[Insight Service]
    ActionLogServiceImpl.recordActionLog()
         ↓
    user_action_logs 테이블 INSERT
```

---

## ✅ 설계 결정 요약

| 항목 | 결정 | 이유 |
|------|------|------|
| 데이터 수집 방식 | Backend DB 조회 | 보안, 무결성, 단순성 |
| API 명세 변경 | 불필요 | 프론트엔드 영향 최소화 |
| 프론트엔드 수정 | 불필요 | 기존 코드 그대로 사용 |
| 성능 영향 | 최소 | JPA 1차 캐시 + 비동기 처리 |
| 장애 격리 | 보장 | @Async + try-catch |

---

## 🚀 구현 순서

1. ✅ Proto 파일 업데이트 (`action_time`, `day_of_week`, `hour_of_day` 추가)
2. ✅ UserActionLogGrpcClient 메서드 업데이트
3. ✅ TaskService 로직 업데이트
4. ⏳ Insight Service Entity 업데이트 (컬럼 추가)
5. ⏳ 통합 테스트

---

**작성일**: 2026-03-06  
**작성자**: Kiro AI Assistant  
**버전**: 1.0
