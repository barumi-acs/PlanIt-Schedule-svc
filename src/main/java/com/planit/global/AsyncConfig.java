package com.planit.global;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 비동기 실행 설정
 * Action Log gRPC 호출을 별도 스레드 풀에서 실행
 * 
 * @since 2026-03-06
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {
    
    /**
     * Action Log 전송 전용 Executor
     * 
     * 설정 근거:
     * - Core Pool Size: 2 (최소 2개 스레드 유지)
     * - Max Pool Size: 5 (최대 5개까지 확장)
     * - Queue Capacity: 100 (대기 큐 크기)
     * - Rejection Policy: CallerRunsPolicy (큐 가득 차면 호출 스레드에서 실행)
     */
    @Bean(name = "actionLogExecutor")
    public Executor actionLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 기본 스레드 수
        executor.setCorePoolSize(2);
        
        // 최대 스레드 수
        executor.setMaxPoolSize(5);
        
        // 대기 큐 크기
        executor.setQueueCapacity(100);
        
        // 스레드 이름 접두사
        executor.setThreadNamePrefix("ActionLog-");
        
        // 큐가 가득 찼을 때 정책: 호출 스레드에서 실행 (데이터 손실 방지)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 애플리케이션 종료 시 대기 중인 작업 완료
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        executor.initialize();
        
        log.info("ActionLog Executor initialized: corePoolSize=2, maxPoolSize=5, queueCapacity=100");
        
        return executor;
    }
}
