WITH u AS (
    INSERT INTO users (username, password_hash)
    VALUES ('demo_user', 'dummy_hash_for_dev')
    RETURNING id
), col_todo AS (
    INSERT INTO columns (user_id, name, position)
    SELECT id, '未着手', 0 FROM u
    RETURNING id
), col_doing AS (
    INSERT INTO columns (user_id, name, position)
    SELECT id, '進行中', 1 FROM u
    RETURNING id
), col_done AS (
    INSERT INTO columns (user_id, name, position)
    SELECT id, '完了', 2 FROM u
    RETURNING id
)
INSERT INTO cards (column_id, title, description, due_date, priority, position)
SELECT id, '要件定義書の作成', '次期機能の要件を整理する', (CURRENT_DATE + INTERVAL '3 day')::date, 'HIGH', 0 FROM col_todo
UNION ALL
SELECT id, 'DB設計レビュー', NULL, NULL, 'MEDIUM', 1 FROM col_todo
UNION ALL
SELECT id, 'API実装', '読み取り系APIの実装', (CURRENT_DATE + INTERVAL '1 day')::date, 'HIGH', 0 FROM col_doing
UNION ALL
SELECT id, '画面モック作成', '主要画面のモックアップ', (CURRENT_DATE + INTERVAL '7 day')::date, 'LOW', 1 FROM col_doing
UNION ALL
SELECT id, '環境構築', 'Docker/PostgreSQLのセットアップ', (CURRENT_DATE - INTERVAL '2 day')::date, 'MEDIUM', 0 FROM col_done;
