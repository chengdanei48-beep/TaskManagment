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
