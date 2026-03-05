package com.planit.task.emoji;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.planit.global.BaseTimeEntity;

import lombok.Getter;
import lombok.Setter;

/**
 * emojis 테이블: 사용 가능한 이모지 목록 (정적 데이터)
 */
@Entity
@Getter
@Setter
@Table(name = "emojis")
@SQLDelete(sql = "UPDATE emojis SET deleted_at = CURRENT_TIMESTAMP(6) WHERE emoji_id = ?")
@SQLRestriction("deleted_at IS NULL")
public class EmojiData extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "emoji_id")
    private Long emojiId;

    @Column(name = "emoji_char", nullable = false)
    private String emojiChar;

    @Column(nullable = false)
    private String name;
}
