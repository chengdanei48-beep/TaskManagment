# DB パスワードは Terraform が自動生成し、SSM Parameter Store に暗号化して保管する。
# EC2 が起動時に取得する。コードや Git には書かない。
# 注意: 生成した値は terraform.tfstate にも入る。tfstate は Git に入れない・他人に渡さない。
resource "random_password" "db" {
  length  = 32
  special = false # .env とコンテナの環境変数で扱いやすい文字だけにする
}

resource "aws_ssm_parameter" "db_password" {
  name  = "/${var.project_name}/db_password"
  type  = "SecureString"
  value = random_password.db.result
}
