-- category_list 초기 데이터
-- 앱 시작 시 자동으로 INSERT (ddl-auto: create 이므로 매번 새로 삽입)
-- 실제 서비스 시 User Service 쪽 데이터와 동기화 예정

INSERT INTO category_list (name) VALUES
('직무/커리어'),
('어학/자격증'),
('독서/학습'),
('건강/운동'),
('재테크/경제'),
('마인드/루틴'),
('취미/관계'),
('기타');

-- emojis 초기 데이터
-- 앱 시작 시 자동으로 INSERT (ddl-auto: create 이므로 매번 새로 삽입)
INSERT INTO emojis (emoji_char, name) VALUES
('🔥', '파이팅'),
('👍', '좋아요'),
('💪', '응원'),
('🎉', '축하'),
('😄', '기쁨'),
('❤️', '사랑'),
('👏', '박수'),
('🌟', '최고');

-- ────────────────────────────────────────────────────────────────────────────
-- friend-user-001 테스트 데이터
-- category_list_id: 1=직무/커리어, 2=어학/자격증, 3=독서/학습, ...
-- ────────────────────────────────────────────────────────────────────────────

-- friend-user-001 카테고리 (직무/커리어)
INSERT INTO category (list_id, user_id, created_at, updated_at)
VALUES (1, 'friend-user-001', NOW(6), NOW(6));

SET @friend_cat_id = LAST_INSERT_ID();

-- friend-user-001 목표
INSERT INTO goals (category_id, title, start_date, end_date, created_at, updated_at)
VALUES (@friend_cat_id, '취업 준비', '2026-03-01', '2026-03-31', NOW(6), NOW(6));

SET @friend_goal_id = LAST_INSERT_ID();

-- friend-user-001 주간 목표
INSERT INTO week_goals (goals_id, title, created_at, updated_at)
VALUES (@friend_goal_id, '알고리즘 & CS 공부', NOW(6), NOW(6));

SET @friend_wg_id = LAST_INSERT_ID();

-- friend-user-001 오늘 할 일
INSERT INTO tasks (week_goals_id, content, complete, target_date, created_at, updated_at)
VALUES (@friend_wg_id, '알고리즘 문제 3개 풀기', false, CURDATE(), NOW(6), NOW(6));

INSERT INTO tasks (week_goals_id, content, complete, target_date, created_at, updated_at)
VALUES (@friend_wg_id, '자료구조 복습하기', false, CURDATE(), NOW(6), NOW(6));
