# TaskManagment

練習用の追記です

## ローカル起動手順(開発時)

1. DB: `docker compose up -d`(`.env` は `.env.example` をコピーして作成)
2. バックエンド: `cd backend && ./mvnw spring-boot:run`(http://localhost:8080)
3. フロントエンド: `cd frontend && npm install && npm run dev`(http://localhost:5173)

フロントエンドの `/api` へのリクエストは、Viteのプロキシでバックエンド(8080)に転送されます。
