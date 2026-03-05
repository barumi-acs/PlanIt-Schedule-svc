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
