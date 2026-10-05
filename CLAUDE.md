# CLAUDE.md

このファイルはこのリポジトリで作業する際にClaude Codeが厳守すべきルールを定める。

## 技術スタック
Java / Spring Boot (JDK25) + React + PostgreSQL

## 開発フロー(厳守)

### 1. 作業開始前に必ずGitHub Issueを作成する
機能追加・修正に着手する前に、`gh issue create` で目的・背景・完了条件を明記したIssueを起票する。Issue番号は以降のブランチ名・PRで参照する。

### 2. Issueごとに専用ブランチを作成する
masterから直接作業しない。必ずIssueに対応する専用ブランチを作成してから作業する。

命名規則: `種別/issue番号-概要`
- 種別: `feature`, `fix`, `chore`, `docs` など
- 例: `feature/12-add-login`, `fix/15-null-pointer`

### 3. masterへの直接pushは禁止。必ずPR経由でマージする
`git push origin master` のような直接pushは行わない(GitHub側のブランチ保護ルールでも拒否される)。

作業完了後は `gh pr create` でPRを作成しマージする。PR本文にはIssue番号への参照(例: `Closes #12`)を含める。

レビュー承認は必須ではないが、PR作成とマージは必ず経由すること。

## 品質レビュー

PRを出す前や、全体を点検するときは、スキル `quality-review`(`.claude/skills/quality-review/SKILL.md`)のチェックリストに沿って確認する。
バックエンドは `./mvnw verify`、フロントエンドは `npm run lint` / `npm run typecheck` / `npm run build` が通ること(CIでも実行される)。

## 動作確認でのサーバー起動(厳守)

動作確認のためにサーバーを起動するときは、必ずアプリの既定ポートで起動する。ポートが競合しても、別のポートに逃げてはならない。

### 既定ポート

| 対象 | ポート | 定義箇所 |
|---|---|---|
| バックエンド(Spring Boot / jar) | 8080 | `backend/src/main/resources/application.properties` の `server.port` |
| フロントエンド(Vite 開発サーバー) | 5173 | `frontend/vite.config.ts` の `server.port`(`strictPort: true`) |
| PostgreSQL(Docker) | 5433 | `docker-compose.yml` / `.env` の `DB_PORT` |

### ルール
1. サーバーを起動する前に、既定ポートが使われていないか確認する。
2. **使われていた場合は、必ずそのポートを使っているプロセスを停止してから、既定ポートで起動する。** `--server.port=8081` のような別ポートでの起動、Vite の自動ポート切り替え(5174 など)、プロキシ先の書き換えで回避することは禁止する。
3. 同じアプリのサーバーがすでに既定ポートで動いている場合も、最新のコードを反映するため、停止して起動し直す。
4. 停止したプロセス(PID・コマンドライン)は、作業報告でユーザーに伝える。
5. 起動後は、既定ポートで応答することを確認する(例: `http://localhost:8080/api/health`、`http://localhost:5173/`)。
6. jar の動作確認(`java -jar`)や、別DBを使った確認など、補助的な起動も同じルールに従い、既定ポート(8080)で行う。
7. PostgreSQL は、自プロジェクトのコンテナ(`taskmanagement-postgres`)が起動済みで healthy ならそのまま使う(停止・再起動しない)。それ以外のプロセスが 5433 を使っている場合は、ルール2に従って停止する。

### ポートを使っているプロセスの停止(PowerShell)
```powershell
$port = 8080   # 対象のポート
$pids = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique
foreach ($p in $pids) {
  Get-CimInstance Win32_Process -Filter "ProcessId=$p" | Select-Object ProcessId, Name, CommandLine  # 報告用に控える
  Stop-Process -Id $p -Force
}
```
停止後、ポートが解放されたこと(`Get-NetTCPConnection -LocalPort $port -State Listen` が何も返さないこと)を確認してから起動する。
