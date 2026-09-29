# タスク管理アプリ 遷移図・データフロー・ER図(DB版)

要件定義書([requirements.md](requirements.md) v2.0)の補足資料です。データはPC内のローカルサーバー経由でデータベース(SQLite)に保存します。
GitHub 上でこのファイルを開くと、以下の図がそのまま表示されます。

画面のモックアップ(見た目)は [screen-design.html](screen-design.html) を参照してください。

---

## 1. 画面遷移図

未ログイン時はログイン画面のみが表示されます。ログイン後にメイン画面(自分のボード)へ移り、追加・編集・削除の確認はダイアログとして重なって表示されます。

```mermaid
flowchart LR
    Login["ログイン画面\n(初回はアカウント登録)"]
    Main["メイン画面\n自分のボード(列とカード)"]
    AddCard["カード追加ダイアログ\n「＋ タスク追加」"]
    EditCard["カード編集ダイアログ\nカードをクリック"]
    DeleteCard["カード削除の確認"]
    AddCol["列追加ダイアログ\n「＋ 列を追加」"]
    DeleteCol["列削除の確認\n列の「︙」から選択"]

    Login -- ログイン成功 --> Main
    Main -- ログアウト --> Login

    Main <--> AddCard
    Main <--> EditCard
    Main <--> AddCol
    Main <--> DeleteCol
    EditCard -- 削除ボタン --> DeleteCard
    DeleteCard -- 削除する/キャンセル --> Main

    AddCard -- 保存/キャンセル --> Main
    EditCard -- 保存/キャンセル --> Main
    AddCol -- 保存/キャンセル --> Main
    DeleteCol -- 削除する/キャンセル --> Main
```

※ ドラッグ&ドロップによるカード移動は、メイン画面の中で完結する操作のため、画面遷移としては表現していません。

---

## 2. データの流れ

画面(ブラウザ)とデータベースの間に、PC内で動く「ローカルサーバー」が入ります。インターネット通信はありません。

```mermaid
flowchart LR
    User["利用者\nログイン・入力\nドラッグ&ドロップ"]
    Browser["画面(ブラウザ)\npublic/*.html\npublic/script.js"]
    Server["ローカルサーバー\nserver/app.js\nログイン確認・入力チェック・期限判定"]
    DB[("データベース\nSQLiteファイル\n(PC内に保存)")]

    User -- 操作 --> Browser
    Browser -- 表示 --> User
    Browser -- "通信(localhost)" --> Server
    Server -- 応答 --> Browser
    Server -- SQL --> DB
    DB -- 結果 --> Server
```

**処理の流れ**

1. **ログイン時**: サーバーがユーザー名・パスワードをDBと照合します。一致すればログイン成功とし、その利用者のデータのみを扱います。
2. **表示時**: サーバーがログイン中の利用者の列・カードだけをDBから取得し、画面に渡します。
3. **操作時**: 追加・編集・削除・移動のたびに、サーバーが入力をチェックしてDBを更新します。
4. **表示更新**: 更新結果を画面が受け取り、描き直します(期限切れの赤表示もこのとき判定)。

※ ローカルサーバーは、そのPCの中だけで通信します(インターネットには接続しません)。

---

## 3. ER図(実装のDB設計)

要件定義書 7章のテーブル定義を図にしたものです。1人の利用者(users)が複数の列(columns)を持ち、1つの列が複数のカード(cards)を持ちます。

```mermaid
erDiagram
    USERS ||--o{ COLUMNS : "持つ"
    COLUMNS ||--o{ CARDS : "持つ"

    USERS {
        int id PK
        string username "重複不可"
        string password_hash "ハッシュ化して保存"
        datetime created_at
    }
    COLUMNS {
        int id PK
        int user_id FK
        string name "1〜20文字、最大10列/人"
        int position "表示順"
    }
    CARDS {
        int id PK
        int column_id FK
        string title "1〜50文字、必須"
        string description "500文字まで、任意"
        date due_date "任意"
        datetime created_at "自動記録"
        int position "列内の表示順"
    }
```

| テーブル | 列名 | 制約(要件定義書 5章・7章より) |
|---|---|---|
| users | username | 必須。重複不可 |
| users | password_hash | 必須。平文では保存しない |
| columns | name | 1〜20文字。利用者ごとに最大10列 |
| columns | user_id | users.id への外部キー |
| cards | title | 1〜50文字、必須 |
| cards | description | 500文字まで、任意 |
| cards | due_date | 任意。未入力なら期限切れ判定なし |
| cards | column_id | columns.id への外部キー |
