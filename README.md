# TaskManagment

Trello のような「かんばん方式」のタスク管理アプリです。列(未着手・進行中・完了など)にタスクの「カード」を並べ、ドラッグ&ドロップで動かして進み具合を見える化します。

- ログイン機能があり、利用者ごとに専用のボードを持ちます(他の利用者のボードは見えません)。
- データはインターネット上ではなく、**お使いのPCの中の PostgreSQL** に保存します。アプリは `127.0.0.1` だけで待ち受け、PCの外からはアクセスできません。
- 無償提供・現状有姿(as-is)です。詳細な前提・制約は [要件定義書](docs/requirements.md) を参照してください。

## 主な機能

| 区分 | 内容 |
|---|---|
| アカウント | 登録(ユーザー名 1〜50文字 / パスワード 8〜72文字)・ログイン・ログアウト。パスワードは BCrypt でハッシュ化して保存 |
| 列 | 追加・削除。登録時に「未着手」「進行中」「完了」の3列を自動作成 |
| カード | 追加・編集・削除。重要度(3段階・色分け)、期限(期限切れは赤表示)、検索 |
| 移動・並び替え | ドラッグ&ドロップで列間の移動と列内の並び替え。優先度順・期限順の並び替えボタン |

機能要件の詳細は [要件定義書](docs/requirements.md) の 5 章、操作の流れは [ユースケース](docs/use-cases.md) を参照してください。

## 技術スタック

| 区分 | 技術 | バージョン |
|---|---|---|
| バックエンド | Java / Spring Boot(Web MVC・Data JPA・Security・Actuator) | JDK 25 / Spring Boot 4.1.1 |
| ビルド | Maven(Maven Wrapper) | 3.9.16 |
| データベース | PostgreSQL(開発は Docker)/ マイグレーションは Flyway | 17(利用者向けは 15 以降を推奨) |
| フロントエンド | React / TypeScript / Vite / React Router | React 19.3.0 / TypeScript 7.0.2 / Vite 8.3.2 / React Router 7.18.4 |
| Lint | Oxlint | 1.86.0 |
| 実行基盤(フロントのビルド) | Node.js / npm | v24.21.0 / 11.19.0 |
| ソース管理 | Git / GitHub | Git 2.55.0(開発環境) |

バージョンの一覧と補足は [要件定義書 6.5](docs/requirements.md) にあります。依存ライブラリを更新したら、あわせて更新してください。

## ディレクトリ構成

```
TaskManagment/
├─ backend/        … バックエンド(Spring Boot)
│  └─ src/main/
│     ├─ java/com/taskmanagement/backend/
│     │  ├─ config/ controller/ service/ repository/ entity/
│     └─ resources/
│        ├─ application.properties … DB接続・ポート等の設定
│        ├─ db/migration/          … Flyway マイグレーション(テーブル定義)
│        └─ db/seed/               … 開発用 seed データ(dev プロファイルのみ)
├─ frontend/       … フロントエンド(React + Vite)
│  └─ src/  api/ auth/ components/ pages/
├─ docs/           … 各種ドキュメント(下記)
├─ docker-compose.yml … 開発用 PostgreSQL
├─ .env.example    … DB接続情報のひな形
├─ SETUP.md        … 利用者向けセットアップ手順
└─ CLAUDE.md       … 開発ルール(Claude Code 向け)
```

## ドキュメント

| ファイル | 内容 |
|---|---|
| [docs/requirements.md](docs/requirements.md) | 要件定義書(機能要件・非機能要件・技術スタック・DB設計) |
| [docs/use-cases.md](docs/use-cases.md) | ユースケース(UC1〜UC10) |
| [docs/diagrams.md](docs/diagrams.md) | 画面遷移図・データフロー・ER図(Mermaid) |
| [docs/screen-design.html](docs/screen-design.html) | 画面イメージ(ブラウザで開く) |
| [SETUP.md](SETUP.md) | 利用者向けセットアップ手順(JDK・PostgreSQL の導入、jar の起動) |
| [CLAUDE.md](CLAUDE.md) | 開発フロー・ポート規約などの開発ルール |
| [docs/claude-code-slash-commands.md](docs/claude-code-slash-commands.md) | Claude Code のスラッシュコマンド一覧 |

## ローカル起動手順(開発時)

前提: JDK 25、Node.js、Docker がインストールされていること。

1. DB: `.env.example` を `.env` にコピーし、`docker compose up -d`
2. バックエンド: `cd backend && ./mvnw spring-boot:run`(http://localhost:8080)
   - 開発用プロファイル(`dev`)で起動し、seedデータ(`demo_user` とサンプルカード)がDBに入ります。`demo_user` はダミーのパスワードのためログインできません。画面からアカウントを登録して使ってください。
3. フロントエンド: `cd frontend && npm install && npm run dev`(http://localhost:5173)

フロントエンドの `/api` へのリクエストは、Vite のプロキシでバックエンド(8080)に転送されます。初回は画面の「アカウント登録はこちら」からアカウントを登録してください。

### 既定ポート

動作確認では、次の既定ポートを使います(ポートが競合したら、別ポートに逃げず、使っているプロセスを止めてから起動します。詳細は [CLAUDE.md](CLAUDE.md))。

| 対象 | ポート |
|---|---|
| バックエンド | 8080 |
| フロントエンド(Vite) | 5173(固定) |
| PostgreSQL(Docker) | 5433 |

### 環境変数

| 環境変数 | 意味 | 既定値 |
|---|---|---|
| `DB_PORT` | PostgreSQL のポート | `5433` |
| `DB_NAME` | データベース名 | `taskmanagement` |
| `DB_USER` | DBユーザー名 | `taskmanagement` |
| `DB_PASSWORD` | DBパスワード | 開発用の値(`.env.example` 参照) |
| `SERVER_ADDRESS` | 待ち受けアドレス | `127.0.0.1` |

## テスト・Lint

```
cd backend && ./mvnw test        # バックエンド(統合テスト。DB の起動が必要)
cd frontend && npm run lint      # フロントエンドの静的解析
cd frontend && npm run build     # 型チェック + ビルド
```

## API

すべて JSON。`/api/auth/register`・`/api/auth/login`・`/api/health` 以外は、ログイン(セッション)が必要です。

| メソッド | パス | 内容 |
|---|---|---|
| POST | `/api/auth/register` | アカウント登録 |
| POST | `/api/auth/login` | ログイン |
| POST | `/api/auth/logout` | ログアウト |
| GET | `/api/auth/me` | ログイン中の利用者を取得 |
| GET / POST | `/api/columns` | 列の一覧 / 追加 |
| DELETE | `/api/columns/{id}` | 列の削除 |
| PUT | `/api/columns/{id}/sort` | 列内のカードを並び替え |
| GET / POST | `/api/cards` | カードの一覧 / 追加 |
| GET / PUT / DELETE | `/api/cards/{id}` | カードの取得 / 更新 / 削除 |
| PUT | `/api/cards/{id}/move` | カードの移動 |
| GET / POST | `/api/labels` | ラベルの一覧 / 登録 |
| DELETE | `/api/labels/{id}` | ラベルの削除 |
| GET | `/api/health` | ヘルスチェック |

## jar にまとめて起動する(配布・利用時)

フロントエンドをビルドして jar に同梱すると、起動するプロセスは1つ(Spring Boot)だけになります。

```
cd backend
./mvnw -Pbundle-frontend package
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

ブラウザで http://localhost:8080 を開きます(DB は `docker compose up -d` などで起動しておきます)。

- ビルドでは Node.js(v24.21.0)が自動で取得され、`frontend/` のビルド結果が jar の `static/` に入ります。
- `npm run dev` を起動したままだと `npm ci` が失敗することがあります。開発サーバーを止めてからビルドしてください。
- `-Pbundle-frontend` を付けない通常の `./mvnw test` / `./mvnw package` では、フロントエンドのビルドは行いません。

利用者向けのセットアップ手順(JDK・PostgreSQLの導入、DB作成、jarの起動)は [SETUP.md](SETUP.md) を参照してください。

### seedデータについて

- 開発用のseedデータ(`backend/src/main/resources/db/seed/`)は、`dev` プロファイルのときだけ投入されます。`./mvnw spring-boot:run` と `./mvnw test` は自動で `dev` になります。
- jar を通常起動(プロファイルなし)したときは投入されないので、利用者のDBに `demo_user` やサンプルカードは入りません。
- jar で seed 入りの開発用DBを作りたいときは、`java -jar ... --spring.profiles.active=dev` で起動します。
- マイグレーションを追加するときは、`db/migration` と `db/seed` でバージョン番号が重複しないようにしてください。

## 開発の進め方

1. 作業前に GitHub Issue を作成する(`gh issue create`)
2. `種別/issue番号-概要` の専用ブランチを作る(例: `feature/12-add-login`)
3. master へ直接 push せず、PR(本文に `Closes #12` など)経由でマージする

詳細は [CLAUDE.md](CLAUDE.md) を参照してください。
