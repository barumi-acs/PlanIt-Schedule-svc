package com.planit.task.emoji;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.planit.global.ApiResponse;
import com.planit.task.emoji.dto.EmojiResponse;

import lombok.RequiredArgsConstructor;

/**
 * GET /api/v1/schedules/emojis - 이모지 목록 조회
 */
@RestController
@RequestMapping("/api/v1/schedules/emojis")
@RequiredArgsConstructor
public class EmojiController {

    private final EmojiService emojiService;

    // GET /api/v1/schedules/emojis - 이모지 목록 전체 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<EmojiResponse>>> getEmojiList() {
        return ResponseEntity.ok(ApiResponse.success(emojiService.getEmojiList()));
    }
}
