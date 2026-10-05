---
name: quality-review
description: このリポジトリ(Spring Boot + React)の全体品質レビュー。標準的な実装からの逸脱と、要件定義書・設計書(docs/)と実装の差異を点検し、静的解析・テストを実行する。「品質チェック」「レビューして」「docsと実装の差異」「PR前の確認」などで使う。
---

# 品質レビュー(quality-review)

機能追加・修正の PR を出す前や、定期的な全体点検で使うチェックリスト。
**ドキュメント(`docs/`)を正として**実装を直す。ただし docs に無い実在機能は、docs 側に追記する。

## 手順

1. 下記「自動チェック」を実行し、全て通す。
2. 「観点チェックリスト」を BE / FE それぞれ点検し、指摘を `ファイル:行` つきでまとめる。
3. 「docs ⇔ 実装の差異」を確認する。差異は docs を正として実装を直す(docs に無い実在機能は docs へ追記)。
4. 修正は Issue → 専用ブランチ → PR(CLAUDE.md の開発フロー)で行う。動作確認のサーバー起動は CLAUDE.md のポート規約に従う。

## 自動チェック

```
cd backend  && ./mvnw verify          # テスト + Spotless(整形) + Checkstyle + SpotBugs
cd backend  && ./mvnw spotless:apply  # 整形の自動修正
cd frontend && npm run lint           # Oxlint
cd frontend && npm run typecheck      # tsc -b
cd frontend && npm run build
```

- テストは PostgreSQL(既定 5433)が起動している前提。Windows では `JAVA_HOME`(JDK 25)が必要。
- CI(`.github/workflows/ci.yml`)でも同じものを実行する。ツールの設定は `backend/checkstyle.xml`、`backend/spotbugs-exclude.xml`、`frontend/.oxlintrc.json`。
- 静的解析の除外は誤検知に限り、理由をコメントで残す。

## 観点チェックリスト

### バックエンド(Spring Boot / JPA / Security)

- 入力検証: DTO に Bean Validation(`@NotBlank` `@Size` 等)があり、Controller の `@RequestBody` に `@Valid` がある。手書きの検証を Service に重ねていない。
- エラー応答: `@RestControllerAdvice`(ProblemDetail)で統一。404 は `ResourceNotFoundException`。Service が `boolean` / `Optional` で「見つからない」を返していない。
- HTTP: 作成は 201 + `Location`。ステータスの使い分け(400/401/403/404/409)が一貫している。Controller はクラス単位の `@RequestMapping`。
- トランザクション: Service は `@Transactional(readOnly = true)` を基本とし、更新メソッドだけ `@Transactional`。`spring.jpa.open-in-view=false` で、遅延ロードは Service 内で完結。
- JPA: N+1 がない(集計は GROUP BY、関連は EntityGraph / fetch join)。LIKE のワイルドカードをエスケープ。エンティティを API で直接返さない(DTO を使う)。
- 競合・制約: 一意制約違反が 500 にならず 409 になる。上限チェック(列10個など)の競合を意識している。
- セキュリティ: 公開パスが最小(`/api/auth/register`・`login`・`health`、静的ファイル)。actuator は health のみ。セッション Cookie(http-only / same-site)、CSRF、パスワードをログ・`toString` に出さない。
- 設定: DB パスワード等は環境変数で上書き可能。`show-sql` など開発用設定は `dev` プロファイルのみ。
- テスト: 正常系だけでなく、境界値(文字数上限)・他人のデータ(404)・未ログイン(401)・CSRF(403)を確認している。

### フロントエンド(React / TypeScript / Vite)

- `npm run lint` / `typecheck` / `build` が警告なしで通る。`tsconfig` は `strict`。
- Hooks: `useEffect` の依存配列が正しい。親の再描画のたびに listener を付け替えない(`useCallback` / ref)。
- エラー処理: ユーザーに URL・ステータスコード等の内部情報を見せない。401 はログイン画面へ戻す(共通処理)。通信失敗を「未ログイン」と混同しない。
- アクセシビリティ: モーダルはネイティブ `<dialog>`(フォーカストラップ・Esc)。入力エラーに `role="alert"` と `aria-invalid` / `aria-describedby`。色だけで情報を伝えない。
- CSRF: 更新系リクエストに `X-XSRF-TOKEN` を付けている。
- 型: `any` / 不要な `!` を使わない。API の型は `types.ts` に集約。

### docs ⇔ 実装の差異(docs を正とする)

`docs/requirements.md`・`use-cases.md`・`diagrams.md`・`screen-design.html` と実装を突き合わせる。

- 画面の文言(ボタン・見出し・ヒント・必須表示)、表示形式(日付 `YYYY/MM/DD`、優先度は「重・中・低」)。
- 入力制限(ユーザー名1〜50、パスワード8〜72、列名1〜20、列数10、タイトル1〜50、説明500、ラベル名1〜20)と、エラー表示の仕様。
- 画面項目・操作(確認用パスワード、削除確認ダイアログ、作成日時の表示 等)。
- API(パス・フィールド・ステータスコード・クエリパラメータ)と DB 定義(型・長さ・制約・値の大文字小文字)。
- docs 同士の整合(参照している要件定義書のバージョン、ER 図とテーブル定義)。
- 新機能を入れたら docs(要件・ユースケース・ER図・画面設計・README)を同じ PR で更新する。
