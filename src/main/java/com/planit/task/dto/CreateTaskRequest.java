package com.planit.task.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateTaskRequest {
    private Long weekGoalsId; // 목표 있음: weekGoalsId 전달, 목표 없음: null
    private String userId; // 목표 없음일 때 소유자 식별용
    private String category; // 화면 표시용 카테고리 (목표 없음 할 일에서 메인 목표 제목 또는 키워드)
    private String content;
    private String targetDate; // yyyy-MM-dd
}
