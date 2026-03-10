package com.planit.task.emoji;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskEmojiRepository extends JpaRepository<TaskEmojiData, Long> {

    // 특정 할 일의 이모지 전체 조회
    List<TaskEmojiData> findByTask_TaskId(Long taskId);

    // 여러 할 일의 이모지 한 번에 조회 (N+1 방지)
    List<TaskEmojiData> findByTask_TaskIdIn(List<Long> taskIds);

    // 특정 할 일 + 특정 이모지 + 특정 유저의 반응 단건 조회 (삭제 시 사용)
    Optional<TaskEmojiData> findByTask_TaskIdAndEmoji_EmojiIdAndUserId(Long taskId, Long emojiId, String userId);

    List<TaskEmojiData> findAllByTask_TaskIdAndEmoji_EmojiIdAndUserId(Long taskId, Long emojiId, String userId);
}
