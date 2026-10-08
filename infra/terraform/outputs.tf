output "app_url" {
  description = "アプリのURL(起動直後は証明書の取得に数分かかる)"
  value       = "https://${local.app_host}"
}

output "tls_internal" {
  description = "自己署名証明書を使っているか(deploy.ps1 のヘルスチェックが参照する)"
  value       = var.tls_internal
}

output "public_ip" {
  description = "サーバーの公開IP(Elastic IP)"
  value       = aws_eip.app.public_ip
}

output "instance_id" {
  description = "EC2 のインスタンスID(SSM 接続に使う)"
  value       = aws_instance.app.id
}

output "artifact_bucket" {
  description = "jar を置く S3 バケット名"
  value       = aws_s3_bucket.artifacts.bucket
}

output "ssm_session_command" {
  description = "サーバーに入るコマンド(SSH 不要)"
  value       = "aws ssm start-session --target ${aws_instance.app.id} --profile ${var.aws_profile} --region ${var.aws_region}"
}
