output "public_ip" {
  description = "サーバーの公開IP(Elastic IP)"
  value       = aws_eip.app.public_ip
}

output "instance_id" {
  description = "EC2 のインスタンスID(SSM 接続に使う)"
  value       = aws_instance.app.id
}

output "ssm_session_command" {
  description = "サーバーに入るコマンド(SSH 不要)"
  value       = "aws ssm start-session --target ${aws_instance.app.id} --profile ${var.aws_profile} --region ${var.aws_region}"
}

output "db_endpoint" {
  description = "RDS の接続先ホスト名(VPC の中からだけ届く)"
  value       = aws_db_instance.main.address
}

output "db_name" {
  description = "RDS のデータベース名"
  value       = aws_db_instance.main.db_name
}

output "db_username" {
  description = "RDS のマスターユーザー名"
  value       = aws_db_instance.main.username
}

output "db_password_parameter" {
  description = "DB パスワードを保管した SSM パラメータ名"
  value       = aws_ssm_parameter.db_password.name
}

output "app_url" {
  description = "アプリのURL(起動直後は証明書の取得に数分かかる)"
  value       = "https://${local.app_host}"
}

output "tls_internal" {
  description = "自己署名証明書を使っているか(deploy.ps1 のヘルスチェックが参照する)"
  value       = local.tls_internal
}

output "artifact_bucket" {
  description = "jar を置く S3 バケット名"
  value       = aws_s3_bucket.artifacts.bucket
}
