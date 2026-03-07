# Schedule Service → Insight Service gRPC 행동 로그 연동 가이드

## 📋 개요

Schedule Service에서 사용자가 할 일을 완료/미루기/삭제할 때, Insight Service로 행동 로그를 비동기로 전송하는 gRPC 통합 구현 가이드입니다.

## 🎯 핵심 요구사항

1. **비동기 처리**: 메인 트랜잭션(할 일 상태 변경)과 분리
2. **장애 격리**: Insight Service 장애 시에도 Schedule Service는 정상 동작
3. **데이터 무결성**: 행동 로그 전송 실패 시에도 메인 로직은 롤백되지 않음

## 🏗️ 아키텍처

```
[Schedule Service]                    [Insight Service]
     TaskService                       ActionLogServiceImpl
         ↓                                     ↑
    @Transactional                             |
    상태 변경 (DB)                              |
         ↓                                     |
    UserActionLogGrpcClient                    |
         ↓ @Async                              |
    별도 스레드 풀                               |
         ↓ gRPC (3s timeout)                   |
         └──────────────────────────────────→  |
                                          user_action_logs
                                          테이블에 INSERT
```

## 📁 구현된 파일

### 1. Proto 정의
- **Schedule Service**: `src/main/proto/action_log_service.proto`
- **Insight Service**: `src/main/proto/action_log_service.proto` (동일 파일 복사 필요)

### 2. gRPC Client (Schedule Service)
- `src/main/java/com/planit/grpc/UserActionLogGrpcClient.java`
- 3개의 비동기 메서드 제공:
  - `recordCompletedAction()` - 완료 행동
  - `recordPostponedAction()` - 미루기 행동
  - `recordDeletedAction()` - 삭제 행동

### 3. 비동기 설정
- `src/main/java/com/planit/global/AsyncConfig.java`
- 전용 스레드 풀: core=2, max=5, queue=100

### 4. gRPC 클라이언트 설정
- `src/main/resources/application.yml`
- Insight Service 주소: `localhost:9092` (기본값)
- 타임아웃: 3초

## 🔧 TaskService 통합 예시

### 1. UserActionLogGrpcClient 주입

```java
@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository taskRepository;
    private final WeekGoalRepository weekGoalRepository;
    private final TaskEmojiRepository taskEmojiRepository;
    private final UserServiceGrpcClient userServiceGrpcClient;
    
    // ✅ 추가: 행동 로그 gRPC 클라이언트
    private final UserActionLogGrpcClient actionLogGrpcClient;
    
    // ... 기존 코드
}
```

### 2. 할 일 완료 토글 (toggleComplete)

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
        // 완료 처리된 경우에만 로그 전송
        String userId = extractUserId(task);
        Long goalsId = extractGoalsId(task);
        
        if (userId != null && goalsId != null) {
            actionLogGrpcClient.recordCompletedAction(
                userId,
                task.getTaskId(),
                goalsId,
                task.getTargetDate()
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

### 3. 할 일 미루기 (postponeTask)

```java
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
        actionLogGrpcClient.recordPostponedAction(
            userId,
            task.getTaskId(),
            goalsId,
            originalDate,
            postponedDate
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

### 4. 할 일 삭제 (deleteTask)

```java
@Transactional
public void deleteTask(Long taskId) {
    TaskData task = taskRepository.findById(taskId)
            .orElseThrow(() -> new CustomException(ErrorCode.S4041));
    
    // 1️⃣ 비동기 행동 로그 전송 (삭제 전에 데이터 추출)
    String userId = extractUserId(task);
    Long goalsId = extractGoalsId(task);
    
    if (userId != null && goalsId != null) {
        actionLogGrpcClient.recordDeletedAction(
            userId,
            task.getTaskId(),
            goalsId,
            task.getTargetDate()
        );
    }
    
    // 2️⃣ 메인 로직: Soft Delete
    taskRepository.deleteById(taskId);
}
```

### 5. Helper 메서드 (userId, goalsId 추출)

```java
/**
 * TaskData에서 userId 추출
 * - 목표 있음: weekGoal → goal → category → userId
 * - 목표 없음: task.userId 직접 사용
 */
private String extractUserId(TaskData task) {
    if (task.getWeekGoal() != null) {
        // 목표 있음: weekGoal → goal → category → userId
        return task.getWeekGoal().getGoal().getCategory().getUserId();
    } else {
        // 목표 없음: task.userId 직접 사용
        return task.getUserId();
    }
}

/**
 * TaskData에서 goalsId 추출
 * - 목표 있음: weekGoal → goal → goalsId
 * - 목표 없음: null (행동 로그 전송 안 함)
 */
private Long extractGoalsId(TaskData task) {
    if (task.getWeekGoal() != null) {
        return task.getWeekGoal().getGoal().getGoalsId();
    }
    return null; // 목표 없음 할 일은 행동 로그 안 남김
}
```

## 🚀 다음 단계

### Insight Service 구현 필요

1. **Proto 파일 복사**
   ```bash
   cp PlanIt-Schedule-svc/src/main/proto/action_log_service.proto \
      PlanIt-Insight-svc/src/main/proto/
   ```

2. **gRPC Server 구현**
   - `ActionLogServiceImpl.java` 생성
   - `RecordActionLog` RPC 메서드 구현
   - `user_action_logs` 테이블에 INSERT

3. **gRPC Server 설정**
   - `application.yml`에 gRPC 서버 포트 9092 설정
   - `@GrpcService` 어노테이션으로 서비스 등록

4. **테스트**
   - Schedule Service에서 할 일 완료/미루기/삭제 실행
   - Insight Service의 `user_action_logs` 테이블 확인
   - Insight Service 중단 시에도 Schedule Service 정상 동작 확인

## 📊 데이터 흐름

```
1. 사용자가 할 일 완료 버튼 클릭
   ↓
2. TaskController → TaskService.toggleComplete()
   ↓
3. @Transactional 시작
   ↓
4. task.setComplete(true) → DB UPDATE
   ↓
5. @Transactional 커밋 (메인 로직 완료)
   ↓
6. actionLogGrpcClient.recordCompletedAction() 호출
   ↓
7. @Async 별도 스레드에서 실행
   ↓
8. gRPC 호출 (3초 타임아웃)
   ↓
9. Insight Service → user_action_logs INSERT
   ↓
10. 성공/실패 여부와 무관하게 Schedule Service는 정상 응답
```

## ⚠️ 주의사항

1. **목표 없음 할 일**: `goalsId`가 null이면 행동 로그 전송 안 함
2. **완료 토글**: 완료 → 미완료는 로그 안 남김 (완료 처리만 기록)
3. **예외 처리**: gRPC 클라이언트 내부에서 모든 예외를 catch하여 로그만 남김
4. **타임아웃**: 3초 초과 시 자동으로 실패 처리 (메인 로직에 영향 없음)
5. **스레드 풀**: 큐가 가득 차면 CallerRunsPolicy로 호출 스레드에서 실행

## 🔍 트러블슈팅

### Insight Service 연결 실패
```
ERROR [actionLogExecutor-1] c.p.g.UserActionLogGrpcClient : 
Failed to record COMPLETED action for user=user123, task=456
io.grpc.StatusRuntimeException: UNAVAILABLE: io exception
```
→ 정상 동작. Schedule Service는 영향 없음.

### 스레드 풀 큐 가득 참
```
WARN [http-nio-8082-exec-1] o.s.s.c.ThreadPoolTaskExecutor : 
Queue capacity reached, executing task in caller thread
```
→ CallerRunsPolicy로 HTTP 요청 스레드에서 실행됨. 응답 시간 약간 증가.

### goalsId null 경고
```
WARN [http-nio-8082-exec-1] c.p.t.TaskService : 
Cannot send action log: goalsId is null for task=789
```
→ 목표 없음 할 일. 정상 동작.

## 📚 참고 문서

- [gRPC Spring Boot Starter](https://github.com/grpc-ecosystem/grpc-spring)
- [Spring @Async 가이드](https://spring.io/guides/gs/async-method/)
- [Circuit Breaker 패턴](https://martinfowler.com/bliki/CircuitBreaker.html)
