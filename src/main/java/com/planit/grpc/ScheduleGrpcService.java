package com.planit.grpc;

import com.planit.category.CategoryData;
import com.planit.category.CategoryRepository;
import com.planit.category.category_list.CategoryList;
import com.planit.category.category_list.CategoryListRepository;
import com.planit.goal.GoalData;
import com.planit.goal.GoalRepository;
import com.planit.grpc.schedule.CreatePlanRequest;
import com.planit.grpc.schedule.Task;
import com.planit.grpc.schedule.WeekGoal;
import com.planit.task.TaskData;
import com.planit.task.TaskRepository;
import com.planit.weekgoal.WeekGoalData;
import com.planit.weekgoal.WeekGoalRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * gRPC로 받은 계획 데이터를 처리하고 DB에 저장하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleGrpcService {

    private final CategoryListRepository categoryListRepository;
    private final CategoryRepository categoryRepository;
    private final GoalRepository goalRepository;
    private final WeekGoalRepository weekGoalRepository;
    private final TaskRepository taskRepository;

    /**
     * gRPC로 받은 계획 데이터를 DB에 물리적으로 저장
     * 
     * @param request Strategy Service에서 받은 CreatePlanRequest
     */
    @Transactional
    public Long createPlan(CreatePlanRequest request) {
        log.info("📡 Strategy에서 실행 계획 수신 및 저장 시작 - userId: {}", request.getUserId());

        // 1. Category 확인 및 생성
        CategoryData category = getOrCreateCategory(request.getUserId(), request.getCategoryName());

        // 2. Goal 저장
        GoalData goal = new GoalData();
        goal.setCategory(category);
        goal.setTitle(request.getGoal().getTitle());
        goal.setStartDate(LocalDate.parse(request.getGoal().getStartDate()));
        goal.setEndDate(LocalDate.parse(request.getGoal().getEndDate()));
        goal = goalRepository.save(goal);
        log.info("🎯 Goal 저장 완료: {}", goal.getGoalsId());

        // 🎯 물리적 정합성 강화: Task를 저장할 때, 인자로 들어온 카테고리보다
        // 실제 생성된 Goal의 카테고리를 우선 참조하여 유실 방지
        final CategoryData finalCategory = (goal.getCategory() != null) ? goal.getCategory() : category;

        // 3. WeekGoal 및 Task 저장
        for (WeekGoal weekGoalDto : request.getGoal().getWeekGoalsList()) {
            WeekGoalData weekGoal = new WeekGoalData();
            weekGoal.setGoal(goal);
            weekGoal.setTitle(weekGoalDto.getTitle());
            weekGoal = weekGoalRepository.save(weekGoal);
            log.info("  📌 WeekGoal 저장 완료: {}", weekGoal.getWeekGoalsId());

            for (Task taskDto : weekGoalDto.getTasksList()) {
                TaskData task = new TaskData();
                task.setWeekGoal(weekGoal);
                task.setContent(taskDto.getContent());
                task.setTargetDate(LocalDate.parse(taskDto.getTargetDate()));
                task.setComplete(false);
                task.setCategory(finalCategory); // 🎯 Category가 이미 userId를 가지고 있음
                taskRepository.save(task);
            }
        }

        log.info("✅ 모든 계획 데이터 DB 저장 완료");
        return goal.getGoalsId();
    }

    /**
     * 유저의 카테고리를 조회하거나 없으면 새로 생성
     */
    private CategoryData getOrCreateCategory(String userId, String categoryName) {
        CategoryList categoryList = categoryListRepository.findByName(categoryName)
                .orElseGet(() -> {
                    log.warn("⚠️ 카테고리 '{}'가 존재하지 않아 '기타'를 사용합니다.", categoryName);
                    return categoryListRepository.findByName("기타")
                            .orElseThrow(() -> new RuntimeException("Default category '기타' not found"));
                });

        return categoryRepository.findByUserIdAndCategoryList_ListId(userId, categoryList.getListId())
                .orElseGet(() -> {
                    log.info("📂 유저({})의 새로운 카테고리 '{}' 생성", userId, categoryName);
                    CategoryData newCategory = new CategoryData();
                    newCategory.setUserId(userId);
                    newCategory.setCategoryList(categoryList);
                    return categoryRepository.save(newCategory);
                });
    }
}
