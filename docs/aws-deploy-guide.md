# AWSデプロイガイド(AWS CLI + Terraform + AI)

TaskManagement を **AWS のマネジメントコンソールを手で操作せず**、コマンド(AWS CLI と Terraform)だけでデプロイするためのガイドです。AWS・Terraform・IaC を初めて触る人を想定し、基礎の解説から順に説明します。

> **このガイドの位置づけ**
> - 解説と手順(このファイル): Issue #61
> - Terraform コード・デプロイスクリプト: Issue #62(`infra/terraform/`、`scripts/deploy.ps1`)
> - アプリは Dockerfile でイメージを作らず、公式の Java イメージ(`eclipse-temurin:25-jre`)に jar を渡して動かします。そのため Dockerfile とイメージの保管場所(ECR)は不要です。

## 目次

1. [このガイドで作るもの(全体像)](#1-このガイドで作るもの全体像)
2. [費用の考え方(「課金なし」について)](#2-費用の考え方課金なしについて)
3. [基礎知識: AWS・IaC・Terraform](#3-基礎知識awsiacterraform)
4. [事前準備(人がやること)](#4-事前準備人がやること)
5. [認証設定(AWS CLI を使えるようにする)](#5-認証設定aws-cli-を使えるようにする)
6. [Terraform コードの読み方](#6-terraform-コードの読み方)
7. [構築・デプロイ・撤収の手順](#7-構築デプロイ撤収の手順)
8. [AI にデプロイさせるときのルール](#8-ai-にデプロイさせるときのルール)
9. [トラブルシュート](#9-トラブルシュート)
10. [発展課題](#10-発展課題)

---

## 1. このガイドで作るもの(全体像)

AWS 上の **EC2(仮想サーバー)1台** に、Docker Compose で3つのコンテナを動かします。

```
インターネット
    │ https://<固定IP>.sslip.io
    ▼
┌─────────────── EC2 (1台) ────────────────┐
│  caddy  ──▶  app (Spring Boot + 画面)     │
│  (HTTPS)       │                          │
│                ▼                          │
│              db (PostgreSQL 17)           │
│              └ データはディスク(EBS)に保存 │
└──────────────────────────────────────────┘
  ・操作用の入口は SSM Session Manager(SSH 不使用)
  ・jar の受け渡しは S3 バケット経由
```

| 部品 | 役割 |
|---|---|
| EC2 | アプリを動かすサーバー |
| Elastic IP | 変わらない公開IPアドレス |
| Caddy | HTTPS(鍵マーク)にする係。証明書は Let's Encrypt から自動取得 |
| `<IP>.sslip.io` | IPアドレスをそのままホスト名にしてくれる無料サービス。独自ドメインが不要 |
| S3 | ビルドした jar を一時的に置く場所 |
| SSM | サーバーへ安全にコマンドを送る仕組み。パスワード(DB)の保管にも使う |
| Budgets | 使いすぎたらメールで知らせる |

**あえて使わないもの**: ALB(ロードバランサー)、NAT Gateway、RDS、Fargate。いずれも **常時課金** で、無料枠のクレジットを速く減らすためです。発展課題として [10章](#10-発展課題) に載せています。

### 制約(知っておくこと)

- **サーバーは1台だけ**。このアプリはログイン状態(セッション)をサーバーのメモリに持つため、複数台にするとログインが共有されません。
- DB は同じサーバーの中です。サーバーのディスクが壊れるとデータも失われます(バックアップは対象外。要件定義書 10章と同じ前提)。
- 既定では、公開URLは **インターネットから誰でも開けます**。不特定多数に使わせたくない場合は、`terraform.tfvars` の `allowed_cidrs` で自分のIPだけに絞れます。
  - 自分のIPは、`curl https://checkip.amazonaws.com` で調べます(インターネットから見えるIPです)。
  - **絞ると公開証明書(Let's Encrypt)が取れなくなる**ので、`tls_internal = true` も指定します。Caddy が自己署名証明書を発行し、通信は暗号化されますが、**ブラウザに「安全ではありません」と警告が出ます**(「詳細設定」→「進む」で開けます)。
  - 家庭用回線ではIPが変わることがあります。入れなくなったら、`allowed_cidrs` を新しいIPに直して `terraform apply` します。

---

## 2. 費用の考え方(「課金なし」について)

結論: **完全な 0 円ではなく、「最初にもらえるクレジットの範囲内で収める」** 設計です。

### 新しい AWS 無料枠(2025年7月15日以降のアカウント)

| 項目 | 内容 |
|---|---|
| 登録時のクレジット | **$100**(自動付与) |
| 追加クレジット | 最大 **$100**(EC2・RDS・Lambda・Bedrock・Budgets などを試す5つの課題で獲得) |
| 期間 | **6か月**、またはクレジットが尽きるまで(早い方) |
| 期限が来ると | **Free プランのアカウントは自動で閉鎖**。90日以内に Paid プランへ切り替えれば復旧 |
| Free プランの制限 | 使えるサービスが限定される |
| Paid プラン | すべてのサービスが使える。クレジットを使い切ったあとは通常の従量課金 |

出典: [AWS Free Tier now offers $200 in credits and 6-month free plan](https://aws.amazon.com/about-aws/whats-new/2025/07/aws-free-tier-credits-month-free-plan/) / [Choosing an AWS Free Tier plan](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/free-tier-plans.html)

> 無料枠の条件は変わることがあります。実際に使う前に、上の公式ページを確認してください。

### この構成の月額目安(クレジットを使い切ったあと)

| 項目 | 目安 |
|---|---|
| EC2 t3.micro(東京) | 約 $10 |
| 公開IPv4アドレス(Elastic IP) | 約 $3.6 |
| EBS(ディスク 20GB) | 約 $2 |
| 合計 | **約 $15 / 月** |

数字は目安です。正確な料金は [AWS 料金計算ツール](https://calculator.aws/) で確認してください。

### 課金事故を避けるチェックリスト

- [ ] アカウントプランが **Free プラン** になっている(4章で確認)
- [ ] Budgets(予算アラート)を設定した(Terraform で作成)
- [ ] 使わない間は `terraform destroy` で全部消す(7章)
- [ ] 高額になりやすいもの(NAT Gateway、ALB、RDS、大きなEC2)を **勝手に追加させない**(8章のルール)
- [ ] 月に一度、コンソールの「請求とコスト管理」で金額を見る(閲覧だけならコンソールで構いません)

---

## 3. 基礎知識: AWS・IaC・Terraform

### 3.1 AWS とは

Amazon が提供する「インターネット越しに借りられるコンピューター・ネットワーク・ストレージ」の集まりです。自分のPCの代わりに、サーバーを必要なときに借りて、使った分だけ払います。

### 3.2 用語集

| 用語 | ひとことで | このプロジェクトでの例 |
|---|---|---|
| リージョン | AWS の施設がある地域 | 東京 `ap-northeast-1` |
| AZ(アベイラビリティゾーン) | リージョン内の別々の建物 | 今回は1つだけ使う |
| VPC | AWS 内の自分専用ネットワーク | 最初から用意されている「デフォルトVPC」を使う |
| サブネット | VPC を区切った一区画 | デフォルトのものを使う |
| セキュリティグループ(SG) | 通信を許可するルール(ファイアウォール) | 80番・443番だけ許可 |
| EC2 | 仮想サーバー | アプリを動かす1台 |
| EBS | EC2 に付けるディスク | DB のデータを保存 |
| Elastic IP | 変わらない固定の公開IP | `https://<IP>.sslip.io` の元 |
| S3 | ファイル置き場 | jar を置く |
| IAM | 「誰が何をしていいか」の権限管理 | 人(ユーザー)と、サーバー用の役割(ロール) |
| IAM ロール | 人ではなく **サービスに与える権限** | EC2 が S3 と SSM を読めるようにする |
| IAM Identity Center | 人がログインして一時的な権限を受け取る仕組み | AWS CLI のログインに使う |
| SSM | サーバーの遠隔操作・設定保管 | SSH の代わりにコマンドを送る |
| ARN | AWS 上の資源の「住所」 | `arn:aws:s3:::bucket-name` |

### 3.3 IaC(Infrastructure as Code)とは

サーバーやネットワークの構成を **コード(テキストファイル)として書いて管理する** 考え方です。

| | コンソール手作業 | IaC |
|---|---|---|
| 手順の記録 | 画面操作を覚えておく/手順書 | コードそのものが記録 |
| 再現 | もう一度クリックする。ミスが出る | コマンド1つで同じ環境を作れる |
| 変更履歴 | 残らない | Git で履歴が残る(今のPRのやり方と同じ) |
| 削除 | 消し忘れて課金が続く | コマンド1つで全部消せる |
| AI との相性 | 画面操作は苦手 | テキストなのでAIが読み書きできる |

### 3.4 Terraform とは

IaC のツールの1つです。「**こうなっていてほしい**」という完成形を書くと、Terraform が現状との差を調べて、足りないものを作り、余分なものを消します(これを **宣言的** と呼びます)。

#### 主な用語

| 用語 | 意味 | 例 |
|---|---|---|
| provider | どのクラウドを操作するかの部品 | `aws` |
| resource | 作る対象1つ | `aws_instance`(EC2) |
| variable | 外から渡す値(設定) | リージョン、メールアドレス |
| output | 作ったあとに表示したい値 | 公開URL |
| data | すでにある資源を参照する | デフォルトVPC |
| state | 「Terraform が何を作ったか」の記録ファイル `terraform.tfstate` | **秘密情報を含むので Git に入れない** |

#### 基本の流れ

```
terraform init      準備(必要な部品をダウンロード)
terraform fmt       コードの整形
terraform validate  文法チェック(AWSに接続しない)
terraform plan      「何を作る/変える/消すか」の予定表を表示(まだ何も変えない)
terraform apply     予定表どおりに実際に作る(確認を求められる)
terraform destroy   作ったものをすべて消す
```

**`plan` で予定を確認してから `apply` する** のが最大のポイントです。AI にやらせるときも、この順序を必ず守らせます(8章)。

#### コードの書き方(例)

```hcl
resource "aws_s3_bucket" "artifacts" {   # 種類 "aws_s3_bucket"、このコード内の名前 "artifacts"
  bucket = "taskmgmt-artifacts-example"  # 設定値
}
```

---

## 4. 事前準備(人がやること)

AWS 側の初回設定には、コンソール操作が避けられない部分があります。**ここだけは手作業** で、1回やれば終わりです。以降のインフラ構築はすべてコマンドです。

### チェックリスト

- [ ] **アカウントプランが Free プラン** になっている
  - コンソール右上のアカウント名 →「請求とコスト管理」→「アカウントプラン」(表示名は変わることがあります)
  - 登録時の $100 クレジットが残っているかも、「クレジット」の画面で確認します
- [ ] **ルートユーザーに MFA(多要素認証)** を設定した
  - ルートユーザー = アカウント作成時のメールアドレスでログインする最強の権限。スマホの認証アプリで保護します
  - **以後、ルートユーザーは日常では使いません**
- [ ] **IAM Identity Center を有効化し、管理用ユーザーを作った**(下記)
- [ ] PC に AWS CLI と Terraform を入れた(5章)

### IAM Identity Center の初期設定(コンソールで1回だけ)

目的: AWS CLI に **長期アクセスキーを作らず**、ブラウザでログインして一時的な権限を受け取る方式にするためです。キーがPCやGitから漏れる事故を避けられます。

1. コンソールで、リージョンを **東京(ap-northeast-1)** にし、「IAM Identity Center」を開く
2. 「有効にする」(組織なしの単一アカウントで構いません)
3. 「ユーザー」→ 管理用ユーザーを作成(自分のメールアドレス。届いたメールでパスワードを設定)
4. 「アクセス許可セット」→ 作成。学習用の個人アカウントでは **AdministratorAccess**(管理者権限)を使うと迷いません
   - Terraform が IAM ロールなどを作るため、強い権限が必要です
   - 他人と共有するアカウントでは、権限を絞ってください
5. 「AWS アカウント」→ 自分のアカウントを選び、作ったユーザーとアクセス許可セットを割り当てる
6. IAM Identity Center の **「AWS アクセスポータルURL」**(`https://d-xxxxxxxxxx.awsapps.com/start`)をメモする(5章で使います)

> コンソールの画面の文言は変わることがあります。分からなくなったら、画面の名前をそのまま AI に伝えて確認してください。

---

## 5. 認証設定(AWS CLI を使えるようにする)

### 5.1 ツールのインストール(PowerShell)

```powershell
winget install Amazon.AWSCLI
winget install Hashicorp.Terraform
```

インストール後は **PowerShell を開き直し**、次で確認します。

```powershell
aws --version
terraform -version
```

### 5.2 SSO ログインの設定

```powershell
aws configure sso
```

対話形式で次を聞かれます。

| 質問 | 入力 |
|---|---|
| SSO session name | `taskmgmt` |
| SSO start URL | 4章でメモしたアクセスポータルURL |
| SSO region | Identity Center を有効にしたリージョン(`ap-northeast-1`) |
| SSO registration scopes | そのまま Enter |
| (ブラウザが開く) | ログインして「許可」 |
| アカウント/ロール | 自分のアカウントとアクセス許可セットを選ぶ |
| CLI default client Region | `ap-northeast-1` |
| CLI default output format | `json` |
| CLI profile name | `taskmgmt` |

設定は `~/.aws/config`(Windows では `C:\Users\<名前>\.aws\config`)に保存されます。**Git の管理外** です。

### 5.3 ログインと確認

```powershell
aws sso login --profile taskmgmt
aws sts get-caller-identity --profile taskmgmt
```

アカウントID・ARN が表示されれば成功です。

- ログインの有効期間は数時間です。切れたら `aws sso login --profile taskmgmt` をやり直します。
- 毎回 `--profile taskmgmt` を付けるか、そのPowerShellで次を設定します。

```powershell
$env:AWS_PROFILE = "taskmgmt"
```

### 5.4 代替: Identity Center が使えない場合

最小限の権限を持つ IAM ユーザーを作り、アクセスキーを `aws configure` で設定する方法もあります。ただし **キーは長期有効で、漏れると不正利用・高額請求の原因になります**。次を守ってください。

- キーをチャット、コード、Git、画像に載せない
- 使わなくなったらキーを無効化・削除する
- ルートユーザーのアクセスキーは **絶対に作らない**

### 5.5 AI に渡してよい情報・渡してはいけない情報

| 渡してよい | 渡してはいけない |
|---|---|
| プロファイル名(`taskmgmt`) | アクセスキー、シークレットキー、セッショントークン |
| アカウントID、リージョン | DB パスワード、`.env` の中身 |
| `terraform plan` の出力(ざっと確認してから) | `terraform.tfstate`(秘密情報を含む) |
| エラーメッセージ(キーが混ざっていないか確認) | `~/.aws/` 配下のファイル |

---

## 6. Terraform コードの読み方

> **段階的に進めます。** 一度に全部を作らず、動作確認をしながら順に増やします。
>
> | 段階 | 作るもの | 確認すること | 状態 |
> |---|---|---|---|
> | 1 | EC2(サーバー)、固定IP、セキュリティグループ、IAM(SSM 接続のみ)、予算アラート | SSM で入れる、Docker が動く、swap がある、外から80番に届く | **現在のコード** |
> | 2 | RDS(PostgreSQL) | EC2 から接続できる | これから |
> | 3 | アプリのデプロイ(S3、Docker Compose、Caddy、`scripts/deploy.ps1`) | 画面が開き、登録・ログインできる | これから |
>
> 段階2・3で使うコード(S3、DB パスワードの保管、docker-compose、Caddy など)は、第1段階では外してあります。Git の履歴(PR #64・#66)に残っているので、そこから戻せます。

**第2段階(RDS)の設計方針:** 毎回作り直す運用で、データは残さない前提です。

| 項目 | 設定 | 理由 |
|---|---|---|
| 削除時のスナップショット | `skip_final_snapshot = true`(残さない) | 保管料を避ける。`false` のままだと、`final_snapshot_identifier` がなく `destroy` が失敗する |
| 自動バックアップ | `backup_retention_period = 0` | 削除後にバックアップが残らないようにする |
| 冗長化 | `multi_az = false` | 課金が約2倍になるのを避ける |
| インスタンス | 最小クラス(例: `db.t4g.micro`) | 学習用なので小さく |
| サブネットグループ | デフォルトVPCの既存サブネット(複数AZ)を束ねる。サブネットは新規作成しない | RDS は2つ以上のAZのサブネットが必須で、デフォルトVPCには既に各AZにある |
| 初期データ | 作り直すたびに空 | Flyway がアプリ起動時にテーブルを作る。シードデータ(`db/seed`)は本番では入らない |

`infra/terraform/` の構成(第1段階):

| ファイル | 内容 |
|---|---|
| `versions.tf` | Terraform と AWS provider のバージョンの固定 |
| `providers.tf` | リージョンとプロファイルの設定 |
| `variables.tf` | 外から渡す設定(メールアドレス等)の定義 |
| `terraform.tfvars.example` | 設定値のひな形。コピーして `terraform.tfvars` を作る(実ファイルは Git 管理外) |
| `network.tf` | デフォルトVPCの参照、セキュリティグループ(80/443を指定した接続元だけ許可) |
| `ec2.tf` | EC2、Elastic IP、ディスク |
| `iam.tf` | EC2 用の IAM ロール(SSM 接続だけ許可) |
| `budget.tf` | $1 を超えそうならメール通知 |
| `outputs.tf` | 公開IP、インスタンスID、SSM 接続コマンドを表示 |
| `templates/user_data.sh` | 初回起動時の初期設定(swap と Docker の導入) |

> `ec2.tf` では、サーバーを作り直さないよう `user_data`(初期設定)の変更を無視する設定にしています。のちにデータが入るためです。`templates/` を変えても既存のサーバーには反映されません。

### アプリを AWS で動かすための設定(環境変数)

アプリのコードは変更せず、環境変数で設定を上書きします。

| 環境変数 | 値 | 理由 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db:5432/taskmanagement` | DB ホストが `localhost` 固定のため、コンテナ名 `db` へ向ける |
| `SERVER_ADDRESS` | `0.0.0.0` | 既定の `127.0.0.1` だとコンテナの外から届かない |
| `SERVER_SERVLET_SESSION_COOKIE_SECURE` | `true` | HTTPS のときだけ Cookie を送る |
| `SERVER_FORWARD_HEADERS_STRATEGY` | `framework` | Caddy の後ろでも HTTPS だと認識させる |
| `DB_USER` / `DB_PASSWORD` / `DB_NAME` | Terraform が生成した値 | DB の認証情報(パスワードは SSM に保管) |

`dev` プロファイルは使わないので、開発用の seed データ(`demo_user`)は入りません。

---

## 7. 構築・デプロイ・撤収の手順

すべて PowerShell(プロジェクトのルートから)で行います。

> 現在のコードは **第1段階(EC2 の土台のみ)** です。7.1 の構築のあと、7.1b の動作確認までを行います。7.2 以降(アプリのデプロイ)は、第3段階のコードを戻してから実行します。

### 7.1 初回構築

```powershell
aws sso login --profile taskmgmt
cd infra\terraform
Copy-Item terraform.tfvars.example terraform.tfvars   # 中身(通知メール等)を自分用に編集
terraform init
terraform fmt
terraform validate
terraform plan -out tfplan        # ← 作られるものを読んで確認する
terraform apply tfplan            # ← 確認後に実行
```

`plan` の出力で見るところ:

- 最後の行 `Plan: N to add, 0 to change, 0 to destroy.`(初回は add のみのはず)
- 作られる資源の種類に、**NAT Gateway・Load Balancer・RDS が含まれていない** こと
- EC2 のインスタンスタイプが `t3.micro` であること

### 7.1b 第1段階の動作確認(EC2 のみ)

`terraform apply` のあと、サーバーが期待どおりかを **ブラウザの画面で** 確認します。

```powershell
.\scripts\check-ec2.ps1          # 確認ページを立てる → 表示された URL をブラウザで開く
.\scripts\check-ec2.ps1 -Stop    # 確認が済んだら止める
```

スクリプトは SSM 経由で、サーバー上に一時的な確認ページ(Caddy コンテナ)を立てます。ページには次が表示されます。

| 表示 | 分かること |
|---|---|
| 「EC2 に外から届いています」 | セキュリティグループ(80番)・Elastic IP が動いている |
| あなたのIP(サーバーから見た) | 許可した接続元(`allowed_cidrs`)と一致しているか |
| インスタンスID・タイプ・AZ・公開IP | 想定どおりの EC2 か(`t3.micro`) |
| Docker のバージョン | 初期設定(`user_data`)で Docker が入った |
| swap・メモリ・ディスク | swap が約1GB、ディスクが約20GB |
| cloud-init の状態 | `status: done`(初期設定がエラーなく完了) |

確認ページを立てる処理で `caddy:2` イメージを取得するため、サーバーからインターネットへ出られることの確認にもなります。許可していないIP(スマホ回線など)から開けないことも、任意で確認できます。確認できたら次の段階(RDS)へ進みます。止めるなら `terraform destroy`(7.5)で消します。

### 7.2 アプリのデプロイ(第3段階)

```powershell
.\scripts\deploy.ps1
```

内部の流れ:

1. `backend` で `./mvnw -Pbundle-frontend package`(画面を同梱した jar を作る)
2. jar を S3 バケットへ `aws s3 cp`
3. SSM でサーバーに「jar を取得して再起動」を指示(`aws ssm send-command`)
4. `https://<IP>.sslip.io/api/health` が応答するまで待つ

### 7.3 動作確認

1. `terraform output` で公開URLを確認し、ブラウザで開く
2. 「アカウント登録はこちら」から登録 → ログイン → 列・カードの追加と移動
3. 失敗したときは9章へ

### 7.4 コードを変えたとき

- アプリを変更 → `.\scripts\deploy.ps1`
- インフラ(`.tf`)を変更 → `terraform plan` → 確認 → `terraform apply`

### 7.5 撤収(課金を止める)

```powershell
cd infra\terraform
terraform destroy
```

- **DB のデータも消えます**。必要なら先にバックアップを取ります(AI に「DBをダンプして S3 に置いて」と頼めます)。
- 終わったら `aws ec2 describe-instances --profile taskmgmt` などで、リソースが残っていないことを確認します。

### 7.6 使うときだけ作る運用(費用を抑える)

クレジットの消費を抑えるため、**使うときに作り、終わったら全部消します**。次の2つのスクリプトで行います。

```powershell
.\scripts\aws-up.ps1      # 使い始め: ログイン確認 → 今のIPを許可 → plan を表示 → yes で apply
# ... 動作確認や作業(例: .\scripts\check-ec2.ps1)...
.\scripts\aws-down.ps1    # 使い終わり: 消えるものを表示 → yes で destroy → 消し残しの確認
```

| 項目 | 内容 |
|---|---|
| 確認 | どちらも **`yes` と入力したときだけ** 実行します。それ以外は何も変更しません。 |
| ログイン | SSO が切れていれば、`aws sso login` を自動で促します(ブラウザで「許可」を押す)。 |
| 接続元IP | `aws-up.ps1` が、今のPCの公開IPを `terraform.tfvars` の `allowed_cidrs` に自動で反映します。 |
| 消し残しの確認 | `aws-down.ps1` が、`Project=taskmgmt` のタグが付いた EC2・Elastic IP・セキュリティグループが残っていないかを問い合わせます。 |

使うときの注意:

- **作るたびに公開IP(Elastic IP)が変わります。** URL も毎回変わるので、`terraform output` で確認します。
- **消すとサーバーの中のデータ(DB など)も消えます。** 第2段階以降の RDS も、スナップショットを残さない方針(6章)なので、`destroy` でデータは完全に消えます。残したいデータが出てきたら、`skip_final_snapshot = false` と復元用の設定を足す必要があります。
- 作ってから使える状態になるまで、数分かかります(初期設定の完了待ち)。`check-ec2.ps1` はその完了を待ちます。
- **消し忘れが最大の費用リスク** です。使い終わったら、必ず `aws-down.ps1` を実行します。AI に作業を頼んだ場合は、「終わったら destroy の plan を出して」と頼みます(8章)。

---

## 8. AI にデプロイさせるときのルール

AI(Claude Code)に AWS を操作させるのは強力ですが、誤ると **課金や削除** につながります。次のルールを守ります。

### 8.1 ルール

1. **必ず `terraform plan` を先に実行させ、内容を人が読んで承認してから `apply` する。**
2. `terraform destroy`、IAM の変更、課金が発生する資源(EC2・ディスク・IPなど)の追加や変更は、**その都度、人に確認する。**
3. コマンドには常に `--profile taskmgmt` を付ける(別のアカウントを誤って操作しない)。
4. 高額なもの(NAT Gateway、ALB、RDS、大きなインスタンス)を **提案なしに追加しない。**
5. 認証情報、`tfstate`、`.env` を表示・コミット・送信しない。
6. 変更は Issue → 専用ブランチ → PR の流れ([CLAUDE.md](../CLAUDE.md))を守る。

### 8.2 AI への依頼テンプレート

**初回構築**
```
docs/aws-deploy-guide.md に従って、infra/terraform を使って初回構築をしてください。
AWSプロファイルは taskmgmt です。
terraform init → fmt → validate → plan まで実行し、plan の内容を日本語で要約してください。
私が承認するまで apply はしないでください。
```

**更新(アプリ)**
```
アプリを修正したので、scripts/deploy.ps1 でデプロイしてください。
完了後に /api/health が応答するか確認し、結果を報告してください。
```

**更新(インフラ)**
```
○○を変更したいです。.tf を修正して terraform plan を実行し、
追加・変更・削除される資源と、費用への影響を説明してください。apply は私の承認後です。
```

**障害調査**
```
https://<IP>.sslip.io にアクセスできません。SSM 経由で docker compose ps と
docker compose logs --tail 100 を確認し、原因を教えてください。
```

**撤収**
```
作業を終えるので、terraform destroy の plan を出してください。
消える資源を一覧で見せて、私の承認を待ってください。
```

---

## 9. トラブルシュート

| 症状 | 原因と対処 |
|---|---|
| `The SSO session ... has expired` / `Token has expired` | ログイン切れ。`aws sso login --profile taskmgmt` |
| `Unable to locate credentials` | プロファイル未指定。`--profile taskmgmt` か `$env:AWS_PROFILE` を設定 |
| `InvalidParameterCombination` / インスタンスタイプが使えない | Free プランの対象外のサイズ。`t3.micro` か、その時点で案内されている対象サイズに変える(AWS の無料枠案内を確認) |
| `terraform apply` が権限エラー | アクセス許可セットの権限不足(4章)。IAM の作成権限が必要 |
| Budgets の作成でエラー | アカウントで請求情報へのアクセスが有効か確認 |
| `https://...` が開かない(証明書エラー) | 起動直後は証明書の取得に数分かかる。続く場合は Caddy のログを確認。Let's Encrypt は同じホスト名の再取得に回数制限あり。`allowed_cidrs` で接続元を絞っているのに `tls_internal = true` でないと、証明書を取得できず HTTPS になりません |
| 警告「この接続ではプライバシーが保護されません」 | `tls_internal = true`(自己署名証明書)のとき正常。「詳細設定」→「進む」で開く |
| 画面は出るがログインできない | Cookie の Secure 設定と HTTPS の認識を確認(`SERVER_FORWARD_HEADERS_STRATEGY`)。`http://` ではなく `https://` で開く |
| アプリが起動しない | SSM でサーバーに入り `docker compose logs app` を確認。DB 接続エラーなら環境変数を確認 |

サーバーに入る方法(SSH 不要):

```powershell
aws ssm start-session --target <instance-id> --profile taskmgmt
```

(`instance-id` は `terraform output` に出ます。事前に Session Manager プラグインが必要になる場合があります。AI に導入を頼めます)

---

## 10. 発展課題

慣れてきたら、次に進めます。どれも **費用が増える** ので、2章の目安を確認してから行います。

| 課題 | 内容 |
|---|---|
| RDS 化 | DB をマネージドにして、バックアップや障害対応を AWS に任せる |
| ALB + ACM + 独自ドメイン | ロードバランサーと正式な証明書。複数台にするならセッションの外部化も必要 |
| Terraform の state を S3 に | チームや複数PCでも安全に共有する(ロックは DynamoDB 不要の S3 ネイティブロックを検討) |
| GitHub Actions から自動デプロイ | AWS の認証に OIDC を使い、キーを置かずにデプロイ |
| ECS Fargate 化 | サーバー管理を不要にする |
