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

/**
 * Insight Service로 사용자 행동 로그를 전송하는 gRPC 클라이언트
 * 
 * 특징:
 * - @Async로 비동기 실행 (메인 트랜잭션과 분리)
 * - 모든 예외를 catch하여 로그만 남김 (메인 로직 보호)
 * - 3초 타임아웃 설정
 */
@Slf4j
@Service
public class UserActionLogGrpcClient {

    @GrpcClient("insight-service")
    private ActionLogServiceGrpc.ActionLogServiceBlockingStub actionLogStub;

    /**
     * 할 일 완료 행동 로그 기록 (비동기)
     * 
     * @param userId 사용자 ID
     * @param taskId 할 일 ID
     * @param goalsId 목표 ID
     * @param dueDate 원래 마감일
     * @param actionTime 행동 시각
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
     * 
     * @param userId 사용자 ID
     * @param taskId 할 일 ID
     * @param goalsId 목표 ID
     * @param originalDueDate 원래 마감일
     * @param postponedToDate 미룬 날짜 (원래 마감일 +1일)
     * @param actionTime 행동 시각
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
     * 
     * @param userId 사용자 ID
     * @param taskId 할 일 ID
     * @param goalsId 목표 ID
     * @param dueDate 원래 마감일
     * @param actionTime 행동 시각
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
