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
