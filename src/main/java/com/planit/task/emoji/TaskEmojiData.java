package com.planit.task.emoji;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.planit.global.BaseTimeEntity;
import com.planit.task.TaskData;

import lombok.Getter;
import lombok.Setter;

/**
 * task_emoji 테이블: 유저가 할 일에 다는 이모지 반응
 *
 * ├ task_emoji_id : PK
 * ├ task_id : FK → tasks.task_id
 * ├ emoji_id : FK → emojis.emoji_id
 * └ user_id : 반응한 유저 (User Service UUID)
 */
@Entity
@Getter
@Setter
@Table(name = "task_emoji")
@SQLDelete(sql = "UPDATE task_emoji SET deleted_at = CURRENT_TIMESTAMP(6) WHERE task_emoji_id = ?")
@SQLRestriction("deleted_at IS NULL")
public class TaskEmojiData extends BaseTimeEntity {

    /** PK: task_emoji.task_emoji_id */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_emoji_id")
    private Long taskEmojiId;

    /** FK → tasks.task_id */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private TaskData task;

    /** FK → emojis.emoji_id */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emoji_id", nullable = false)
    private EmojiData emoji;

    /**
     * 반응한 유저 식별자 (User Service의 UUID)
     * MSA 독립 DB 구조이므로 외래키 제약 없음 → 문자열로 관리
     */
    @Column(name = "user_id", nullable = false)
    private String userId;
}
