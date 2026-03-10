package com.planit.grpc;

import com.planit.grpc.schedule.CreatePlanRequest;
import com.planit.grpc.schedule.CreatePlanResponse;
import com.planit.grpc.schedule.ScheduleServiceGrpc;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * Strategy Service로부터 AI 생성 계획을 받는 gRPC 서버
 * 
 * <p>
 * <b>역할:</b>
 * - gRPC 요청 수신
 * - ScheduleGrpcService에 처리 위임
 * - 수신 성공 응답 반환
 * 
 * <p>
 * <b>현재 동작:</b>
 * - DB 저장 없이 수신 확인만 수행
 * - 로그로 데이터 출력
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ScheduleGrpcController extends ScheduleServiceGrpc.ScheduleServiceImplBase {

    private final ScheduleGrpcService scheduleGrpcService;

    @Override
    public void createPlan(CreatePlanRequest request,
            StreamObserver<CreatePlanResponse> responseObserver) {

        try {
            log.info("📡 gRPC 요청 수신 - userId: {}, goal: {}",
                    request.getUserId(), request.getGoal().getTitle());

            // ScheduleGrpcService에 처리 위임 및 goalId 반환
            Long goalId = scheduleGrpcService.createPlan(request);

            // 성공 응답
            CreatePlanResponse response = CreatePlanResponse.newBuilder()
                    .setGoalId(goalId)
                    .setSuccess(true)
                    .setMessage("Plan received successfully")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("✅ gRPC 응답 완료 - success: true");

        } catch (Exception e) {
            log.error("❌ gRPC 요청 처리 실패", e);

            // 실패 응답
            CreatePlanResponse response = CreatePlanResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to receive plan: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}
