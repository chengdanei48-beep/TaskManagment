variable "aws_region" {
  description = "デプロイ先のリージョン(既定は東京)"
  type        = string
  default     = "ap-northeast-1"
}

variable "aws_profile" {
  description = "AWS CLI のプロファイル名(aws configure sso で作ったもの)"
  type        = string
  default     = "taskmgmt"
}

variable "project_name" {
  description = "リソース名の接頭辞"
  type        = string
  default     = "taskmgmt"
}

variable "instance_type" {
  description = "EC2 のインスタンスタイプ。Free プランで使える種類かは AWS の案内で確認する"
  type        = string
  default     = "t3.micro"
}

variable "volume_size_gb" {
  description = "EC2 のディスク容量(GB)。DB のデータもここに入る"
  type        = number
  default     = 20
}

variable "allowed_cidrs" {
  description = "80/443 を許可する接続元。既定は全世界。自分のIPだけにするなら [\"203.0.113.5/32\"] のように指定する"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "budget_email" {
  description = "予算超過の通知先メールアドレス"
  type        = string
}

variable "budget_limit_usd" {
  description = "月の予算(USD)。超えそう・超えたときにメールで知らせる"
  type        = number
  default     = 1
}

variable "db_instance_class" {
  description = "RDS のインスタンスクラス。学習用なので最小クラス"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_storage_gb" {
  description = "RDS のディスク容量(GB)。gp3 の最小は 20"
  type        = number
  default     = 20
}

variable "db_name" {
  description = "RDS 内に作るデータベース名(開発用 docker-compose と同じ)"
  type        = string
  default     = "taskmanagement"
}

variable "db_username" {
  description = "RDS のマスターユーザー名(開発用 docker-compose と同じ)"
  type        = string
  default     = "taskmanagement"
}
