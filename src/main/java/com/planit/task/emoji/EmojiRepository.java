package com.planit.task.emoji;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmojiRepository extends JpaRepository<EmojiData, Long> {

    Optional<EmojiData> findByEmojiId(Long emojiId);
}
