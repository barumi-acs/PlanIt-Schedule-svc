package com.planit.weekgoal;

import com.planit.global.CustomException;
import com.planit.global.ErrorCode;
import com.planit.goal.GoalData;
import com.planit.goal.GoalRepository;
import com.planit.task.TaskData;
import com.planit.task.TaskRepository;
import com.planit.weekgoal.dto.CreateWeekGoalRequest;
import com.planit.weekgoal.dto.UpdateWeekGoalRequest;
import com.planit.weekgoal.dto.UpdateWeekGoalResponse;
import com.planit.weekgoal.dto.WeekGoalListItem;
import com.planit.weekgoal.dto.WeekGoalResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeekGoalService {

    private final WeekGoalRepository weekGoalRepository;
    private final GoalRepository goalRepository;
    private final TaskRepository taskRepository;

    // 1. 주간 목표 생성
    @Transactional
    public WeekGoalResponse createWeekGoal(Long goalsId, CreateWeekGoalRequest req) {
        log.debug("[Schedule-Service] 주간 목표 생성 시작 | goalsId={}", goalsId);
        // 부모 목표 존재 여부 확인
        GoalData goalData = goalRepository.findById(goalsId)
                .orElseThrow(() -> {
                    log.warn("[Schedule-Service] 주간 목표 생성 실패 - 부모 목표 없음 | goalsId={}", goalsId);
                    return new CustomException(ErrorCode.S4042);
                });

        if (req.getTitle() == null || req.getTitle().isBlank()) {
            log.warn("[Schedule-Service] 주간 목표 생성 실패 - 제목 없음 | goalsId={}", goalsId);
            throw new CustomException(ErrorCode.C4001);
        }

        WeekGoalData weekGoal = new WeekGoalData();
        weekGoal.setGoal(goalData);
        weekGoal.setTitle(req.getTitle());
        WeekGoalData saved = weekGoalRepository.save(weekGoal);
        log.info("[Schedule-Service] 주간 목표 생성 완료 | weekGoalsId={}, goalsId={}", saved.getWeekGoalsId(), goalsId);

        return toResponse(saved);
    }

    // 2. 주간 목표 목록 조회
    @Transactional(readOnly = true)
    public List<WeekGoalListItem> getWeekGoals(Long goalsId) {
        log.debug("[Schedule-Service] 주간 목표 목록 조회 시작 | goalsId={}", goalsId);
        // 부모 목표 존재 여부 확인
        goalRepository.findById(goalsId)
                .orElseThrow(() -> {
                    log.warn("[Schedule-Service] 주간 목표 조회 실패 - 부모 목표 없음 | goalsId={}", goalsId);
                    return new CustomException(ErrorCode.S4042);
                });

        List<WeekGoalListItem> result = weekGoalRepository.findByGoal_GoalsId(goalsId)
                .stream()
                .map(this::toListItem)
                .collect(Collectors.toList());
        log.info("[Schedule-Service] 주간 목표 목록 조회 완료 | goalsId={}, count={}", goalsId, result.size());
        return result;
    }

    // 3. 주간 목표 수정
    @Transactional
    public UpdateWeekGoalResponse updateWeekGoal(Long goalsId, Long weekGoalsId, UpdateWeekGoalRequest req) {
        log.debug("[Schedule-Service] 주간 목표 수정 시작 | goalsId={}, weekGoalsId={}", goalsId, weekGoalsId);
        goalRepository.findById(goalsId)
                .orElseThrow(() -> {
                    log.warn("[Schedule-Service] 주간 목표 수정 실패 - 부모 목표 없음 | goalsId={}", goalsId);
                    return new CustomException(ErrorCode.S4042);
                });

        WeekGoalData weekGoal = weekGoalRepository.findById(weekGoalsId)
                .orElseThrow(() -> {
                    log.warn("[Schedule-Service] 주간 목표 수정 실패 - 주간 목표 없음 | weekGoalsId={}", weekGoalsId);
                    return new CustomException(ErrorCode.S4042);
                });

        if (req.getTitle() == null || req.getTitle().isBlank()) {
            log.warn("[Schedule-Service] 주간 목표 수정 실패 - 제목 없음 | weekGoalsId={}", weekGoalsId);
            throw new CustomException(ErrorCode.C4001);
        }

        weekGoal.setTitle(req.getTitle());
        log.info("[Schedule-Service] 주간 목표 수정 완료 | weekGoalsId={}", weekGoalsId);
        // dirty checking → 트랜잭션 커밋 시 JPA가 자동 UPDATE
        return UpdateWeekGoalResponse.builder()
                .weekGoalsId(weekGoal.getWeekGoalsId())
                .title(weekGoal.getTitle())
                .updatedAt(weekGoal.getUpdatedAt())
                .build();
    }

    // 4. 주간 목표 삭제 (Soft Delete)
    @Transactional
    public void deleteWeekGoal(Long goalsId, Long weekGoalsId) {
        log.debug("[Schedule-Service] 주간 목표 삭제 시작 | goalsId={}, weekGoalsId={}", goalsId, weekGoalsId);
        goalRepository.findById(goalsId)
                .orElseThrow(() -> {
                    log.warn("[Schedule-Service] 주간 목표 삭제 실패 - 부모 목표 없음 | goalsId={}", goalsId);
                    return new CustomException(ErrorCode.S4042);
                });

        weekGoalRepository.findById(weekGoalsId)
                .orElseThrow(() -> {
                    log.warn("[Schedule-Service] 주간 목표 삭제 실패 - 주간 목표 없음 | weekGoalsId={}", weekGoalsId);
                    return new CustomException(ErrorCode.S4042);
                });

        weekGoalRepository.deleteById(weekGoalsId); // @SQLDelete 작동 → UPDATE week_goals SET deleted_at = ... WHERE
                                                    // week_goals_id = ?
        log.info("[Schedule-Service] 주간 목표 삭제 완료 | weekGoalsId={}", weekGoalsId);
    }

    // WeekGoalData → WeekGoalResponse
    private WeekGoalResponse toResponse(WeekGoalData w) {
        return WeekGoalResponse.builder()
                .weekGoalsId(w.getWeekGoalsId())
                .goalsId(w.getGoal().getGoalsId())
                .title(w.getTitle())
                .createdAt(w.getCreatedAt())
                .updatedAt(w.getUpdatedAt())
                .build();
    }

    // WeekGoalData → WeekGoalListItem
    private WeekGoalListItem toListItem(WeekGoalData w) {
        List<TaskData> tasks = taskRepository.findByWeekGoal_WeekGoalsId(w.getWeekGoalsId());
        int total = tasks.size();
        int completed = (int) tasks.stream().filter(TaskData::isComplete).count();
        int progressRate = total == 0 ? 0 : (completed * 100 / total);
        return WeekGoalListItem.builder()
                .weekGoalsId(w.getWeekGoalsId())
                .title(w.getTitle())
                .progressRate(progressRate)
                .createdAt(w.getCreatedAt())
                .build();
    }
}
