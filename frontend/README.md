# フロントエンド

React 19 + TypeScript + Vite のSPA。画面仕様・要件は `../docs/` を参照してください。

## コマンド

```
npm ci              # 依存関係のインストール
npm run dev         # 開発サーバー(5173番ポート固定。/api は 8080 へプロキシ)
npm run lint        # 静的解析(Oxlint)
npm run lint:fix    # 自動修正できる指摘を修正
npm run typecheck   # 型チェック(tsc -b)
npm run build       # 型チェック + 本番ビルド
```

## Lint の設定

`.oxlintrc.json` で設定しています(`correctness` カテゴリを error、React Hooks の依存配列などを有効化)。
CI(`.github/workflows/ci.yml`)でも lint・型チェック・ビルドを実行します。
