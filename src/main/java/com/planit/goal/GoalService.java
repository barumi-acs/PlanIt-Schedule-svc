package com.planit.goal;

import com.planit.category.category_list.CategoryList;
import com.planit.category.category_list.CategoryListRepository;
import com.planit.global.CustomException;
import com.planit.global.ErrorCode;
import com.planit.goal.dto.CreateGoalRequest;
import com.planit.goal.dto.GoalDetailResponse;
import com.planit.goal.dto.GoalResponse;
import com.planit.goal.dto.UpdateGoalRequest;
import com.planit.goal.dto.UpdateGoalResponse;
import com.planit.weekgoal.WeekGoalRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;
    private final CategoryListRepository categoryListRepository;
    private final WeekGoalRepository weekGoalRepository;

    // 1. 목표 생성
    @Transactional
    public GoalResponse createGoal(CreateGoalRequest req) {
        // 날짜 파싱
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(req.getStartDate());
            end = LocalDate.parse(req.getEndDate());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new CustomException(ErrorCode.C4001);
        }

        // category_list 테이블에서 이름으로 list_id 조회 (category가 없으면 null 허용)
        Long listId = null;
        if (req.getCategory() != null && !req.getCategory().isBlank()) {
            listId = categoryListRepository.findByName(req.getCategory())
                    .map(CategoryList::getListId)
                    .orElseThrow(() -> new CustomException(ErrorCode.C4041));
        }

        // DB에 INSERT
        GoalData goal = new GoalData();
        goal.setListId(listId);
        goal.setTitle(req.getTitle());
        goal.setStartDate(start);
        goal.setEndDate(end);
        GoalData saved = goalRepository.save(goal);

        return toResponse(saved);
    }

    // 2. 목표 전체 조회
    @Transactional(readOnly = true)
    public List<GoalResponse> getGoals() {
        return goalRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // 3. 목표 삭제 (Soft Delete) - @SQLDelete가 가로채서 UPDATE deleted_at 실행
    @Transactional
    public void deleteGoal(Long id) {
        goalRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.S4042));
        goalRepository.deleteById(id); // @SQLDelete 작동 → UPDATE goals SET deleted_at = ... WHERE id = ?
    }

    // 4. 목표 단건 조회
    @Transactional(readOnly = true)
    public GoalDetailResponse getGoal(Long id) {
        GoalData goal = goalRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.S4042));
        List<GoalDetailResponse.WeekGoalSummary> weekGoalList = weekGoalRepository.findByGoalsId(id)
                .stream()
                .map(w -> GoalDetailResponse.WeekGoalSummary.builder()
                        .weekGoalsId(w.getWeekGoalsId())
                        .title(w.getTitle())
                        .createdAt(w.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return GoalDetailResponse.builder()
                .goalsId(goal.getGoalsId())
                .title(goal.getTitle())
                .startDate(goal.getStartDate())
                .endDate(goal.getEndDate())
                .progressRate(0) // TODO: 주차 목표 연동 후 계산
                .weekGoals(weekGoalList)
                .createdAt(goal.getCreatedAt())
                .updatedAt(goal.getUpdatedAt())
                .build();
    }

    // 5. 목표 수정
    @Transactional
    public UpdateGoalResponse updateGoal(Long id, UpdateGoalRequest req) {
        GoalData goal = goalRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.S4042));
        try {
            goal.setTitle(req.getTitle());
            goal.setStartDate(LocalDate.parse(req.getStartDate()));
            goal.setEndDate(LocalDate.parse(req.getEndDate()));
        } catch (DateTimeParseException | NullPointerException e) {
            throw new CustomException(ErrorCode.C4001);
        }
        // dirty checking → 트랜잭션 커밋 시 JPA가 자동 UPDATE
        return UpdateGoalResponse.builder()
                .goalsId(goal.getGoalsId())
                .title(goal.getTitle())
                .startDate(goal.getStartDate())
                .endDate(goal.getEndDate())
                .updatedAt(goal.getUpdatedAt())
                .build();
    }

    // GoalData → GoalResponse 변환
    private GoalResponse toResponse(GoalData goal) {
        return GoalResponse.builder()
                .goalsId(goal.getGoalsId())
                .listId(goal.getListId())
                .title(goal.getTitle())
                .startDate(goal.getStartDate())
                .endDate(goal.getEndDate())
                .createdAt(goal.getCreatedAt())
                .updatedAt(goal.getUpdatedAt())
                .build();
    }
}
