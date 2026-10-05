# TaskManagment

練習用の追記です

## ローカル起動手順(開発時)

1. DB: `docker compose up -d`(`.env` は `.env.example` をコピーして作成)
2. バックエンド: `cd backend && ./mvnw spring-boot:run`(http://localhost:8080)。開発用プロファイル(`dev`)で起動し、開発用のseedデータ(`demo_user` とサンプルカード)がDBに入ります(`demo_user` はダミーのパスワードのためログインできません。画面からアカウントを登録して使ってください)
3. フロントエンド: `cd frontend && npm install && npm run dev`(http://localhost:5173)

フロントエンドの `/api` へのリクエストは、Viteのプロキシでバックエンド(8080)に転送されます。

初回は画面の「アカウント登録はこちら」からアカウントを登録してください(登録すると「未着手」「進行中」「完了」の3列が作られます)。

## jar にまとめて起動する(配布・利用時)

フロントエンドをビルドして jar に同梱すると、起動するプロセスは1つ(Spring Boot)だけになります。

```
cd backend
./mvnw -Pbundle-frontend package
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

ブラウザで http://localhost:8080 を開きます(DB は `docker compose up -d` などで起動しておきます)。
ポートを変えるときは `--server.port=8081` のように指定します。

- ビルドでは Node.js(v24.21.0)が自動で取得され、`frontend/` のビルド結果が jar の `static/` に入ります。
- `npm run dev` を起動したままだと `npm ci` が失敗することがあります。開発サーバーを止めてからビルドしてください。
- `-Pbundle-frontend` を付けない通常の `./mvnw test` / `./mvnw package` では、フロントエンドのビルドは行いません。

利用者向けのセットアップ手順(JDK・PostgreSQLの導入、DB作成、jarの起動)は [SETUP.md](SETUP.md) を参照してください。

### seedデータについて

- 開発用のseedデータ(`backend/src/main/resources/db/seed/`)は、`dev` プロファイルのときだけ投入されます。`./mvnw spring-boot:run` と `./mvnw test` は自動で `dev` になります。
- jar を通常起動(プロファイルなし)したときは投入されないので、利用者のDBに `demo_user` やサンプルカードは入りません。
- jar で seed 入りの開発用DBを作りたいときは、`java -jar ... --spring.profiles.active=dev` で起動します。
- マイグレーションを追加するときは、`db/migration` と `db/seed` でバージョン番号が重複しないようにしてください。
