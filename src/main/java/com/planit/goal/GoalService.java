package com.planit.goal;

import com.planit.category.CategoryData;
import com.planit.category.CategoryRepository;
import com.planit.category.category_list.CategoryList;
import com.planit.category.category_list.CategoryListRepository;
import com.planit.global.CustomException;
import com.planit.global.ErrorCode;
import com.planit.goal.dto.CreateGoalRequest;
import com.planit.goal.dto.GoalDetailResponse;
import com.planit.goal.dto.GoalResponse;
import com.planit.goal.dto.UpdateGoalRequest;
import com.planit.goal.dto.UpdateGoalResponse;
import com.planit.task.TaskData;
import com.planit.task.TaskRepository;
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
    private final CategoryRepository categoryRepository;
    private final CategoryListRepository categoryListRepository;
    private final WeekGoalRepository weekGoalRepository;
    private final TaskRepository taskRepository;

    // 1. 목표 생성
    @Transactional
    public GoalResponse createGoal(String userId, CreateGoalRequest req) {
        if (userId == null || userId.isBlank()) {
            throw new CustomException(ErrorCode.C4001);
        }
        // 날짜 파싱
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(req.getStartDate());
            end = LocalDate.parse(req.getEndDate());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new CustomException(ErrorCode.C4001);
        }

        // category_list에서 이름으로 list_id 조회 (final → 람다 캡처 가능)
        final CategoryList foundCategoryList = (req.getCategory() != null && !req.getCategory().isBlank())
                ? categoryListRepository.findByName(req.getCategory())
                        .orElseThrow(() -> new CustomException(ErrorCode.C4041))
                : null;

        // category 테이블에서 (userId + listId) → category 조회, 없으면 새로 생성
        CategoryData category = null;
        if (foundCategoryList != null) {
            category = categoryRepository.findByUserIdAndCategoryList_ListId(userId, foundCategoryList.getListId())
                    .orElseGet(() -> {
                        CategoryData newCategory = new CategoryData();
                        newCategory.setUserId(userId);
                        newCategory.setCategoryList(foundCategoryList);
                        return categoryRepository.save(newCategory);
                    });
        }

        GoalData goal = new GoalData();
        goal.setCategory(category);
        goal.setTitle(req.getTitle());
        goal.setStartDate(start);
        goal.setEndDate(end);
        GoalData saved = goalRepository.save(goal);

        return toResponse(saved);
    }

    // 2. 목표 전체 조회 (userId 기준으로 필터)
    @Transactional(readOnly = true)
    public List<GoalResponse> getGoals(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new CustomException(ErrorCode.C4001);
        }
        return goalRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // 3. 목표 삭제 (Soft Delete)
    @Transactional
    public void deleteGoal(Long id) {
        goalRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.S4042));
        goalRepository.deleteById(id);
    }

    // 4. 목표 단건 조회
    @Transactional(readOnly = true)
    public GoalDetailResponse getGoal(Long id) {
        GoalData goal = goalRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.S4042));

        List<GoalDetailResponse.WeekGoalSummary> weekGoalList = weekGoalRepository.findByGoal_GoalsId(id)
                .stream()
                .map(w -> {
                    List<TaskData> tasks = taskRepository.findByWeekGoal_WeekGoalsId(w.getWeekGoalsId());
                    int total = tasks.size();
                    int completed = (int) tasks.stream().filter(TaskData::isComplete).count();
                    int wProgressRate = total == 0 ? 0 : (completed * 100 / total);
                    return GoalDetailResponse.WeekGoalSummary.builder()
                            .weekGoalsId(w.getWeekGoalsId())
                            .title(w.getTitle())
                            .progressRate(wProgressRate)
                            .createdAt(w.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        int goalProgressRate = weekGoalList.isEmpty() ? 0
                : (int) weekGoalList.stream()
                        .mapToInt(GoalDetailResponse.WeekGoalSummary::getProgressRate)
                        .average()
                        .orElse(0);

        return GoalDetailResponse.builder()
                .goalsId(goal.getGoalsId())
                .title(goal.getTitle())
                .startDate(goal.getStartDate())
                .endDate(goal.getEndDate())
                .progressRate(goalProgressRate)
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
                .categoryId(goal.getCategory() != null ? goal.getCategory().getCategoryId() : null)
                .title(goal.getTitle())
                .startDate(goal.getStartDate())
                .endDate(goal.getEndDate())
                .createdAt(goal.getCreatedAt())
                .updatedAt(goal.getUpdatedAt())
                .build();
    }
}
