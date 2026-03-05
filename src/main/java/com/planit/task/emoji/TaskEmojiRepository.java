package com.planit.task.emoji;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskEmojiRepository extends JpaRepository<TaskEmojiData, Long> {

    // 특정 할 일의 이모지 전체 조회
    List<TaskEmojiData> findByTask_TaskId(Long taskId);

    // 여러 할 일의 이모지 한 번에 조회 (N+1 방지)
    List<TaskEmojiData> findByTask_TaskIdIn(List<Long> taskIds);
}
