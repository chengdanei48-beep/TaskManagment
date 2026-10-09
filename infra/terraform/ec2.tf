# Amazon Linux 2023 の最新 AMI(サーバーのひな形)を、AWS が公開しているパラメータから取得する
data "aws_ssm_parameter" "al2023" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

# 変わらない公開IP
resource "aws_eip" "app" {
  domain = "vpc"

  tags = {
    Name = "${var.project_name}-app"
  }
}

resource "aws_instance" "app" {
  ami                    = data.aws_ssm_parameter.al2023.value
  instance_type          = var.instance_type
  subnet_id              = data.aws_subnets.default.ids[0]
  vpc_security_group_ids = [aws_security_group.web.id]
  iam_instance_profile   = aws_iam_instance_profile.app.name

  # 起動直後にパッケージを取得するため、一時的な公開IPも付ける(のちに Elastic IP が割り当たる)
  associate_public_ip_address = true

  metadata_options {
    http_tokens   = "required" # IMDSv2 のみ
    http_endpoint = "enabled"
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = var.volume_size_gb
    encrypted   = true
  }

  user_data = file("${path.module}/templates/user_data.sh")

  lifecycle {
    # user_data や AMI が変わっても、サーバーを作り直さない(のちにデータが入るため)。
    # 設定を変えたいときは、サーバーに入って更新するか、意図して replace する。
    ignore_changes = [user_data, ami]
  }

  tags = {
    Name = "${var.project_name}-app"
  }
}

resource "aws_eip_association" "app" {
  instance_id   = aws_instance.app.id
  allocation_id = aws_eip.app.id
}
