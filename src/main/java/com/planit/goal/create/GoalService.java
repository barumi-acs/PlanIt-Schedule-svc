package com.planit.goal.create;

import com.planit.category.category_list.CategoryList;
import com.planit.category.category_list.CategoryListRepository;
import com.planit.global.CustomException;
import com.planit.global.ErrorCode;
import com.planit.goal.create.dto.CreateGoalRequest;
import com.planit.goal.create.dto.GoalResponse;

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

        // category_list 테이블에서 이름으로 id 조회 (category가 없으면 null 허용)
        Long categoryId = null;
        if (req.getCategory() != null && !req.getCategory().isBlank()) {
            categoryId = categoryListRepository.findByName(req.getCategory())
                    .map(CategoryList::getId)
                    .orElseThrow(() -> new CustomException(ErrorCode.C4041));
        }

        // DB에 INSERT
        GoalData goal = new GoalData();
        goal.setCategoryId(categoryId);
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
                .orElseThrow(() -> new CustomException(ErrorCode.C4041));
        goalRepository.deleteById(id); // @SQLDelete 작동 → UPDATE goals SET deleted_at = ... WHERE id = ?
    }

    // GoalData → GoalResponse 변환
    private GoalResponse toResponse(GoalData goal) {
        return GoalResponse.builder()
                .goalsId(goal.getId())
                .categoryId(goal.getCategoryId())
                .title(goal.getTitle())
                .startDate(goal.getStartDate())
                .endDate(goal.getEndDate())
                .createdAt(goal.getCreatedAt())
                .updatedAt(goal.getUpdatedAt())
                .build();
    }
}
