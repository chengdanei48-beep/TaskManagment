# AWS が最初から用意している「デフォルトVPC」を使う(NAT Gateway など課金されるものを作らない)
data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }

  filter {
    name   = "default-for-az"
    values = ["true"]
  }
}

# 80(HTTP: 証明書の取得とHTTPSへの転送)と 443(HTTPS)だけ許可する。SSH(22)は開けない。
resource "aws_security_group" "web" {
  name        = "${var.project_name}-web"
  description = "Allow HTTP and HTTPS"
  vpc_id      = data.aws_vpc.default.id

  tags = {
    Name = "${var.project_name}-web"
  }
}

resource "aws_vpc_security_group_ingress_rule" "http" {
  for_each = toset(var.allowed_cidrs)

  security_group_id = aws_security_group.web.id
  description       = "HTTP"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  cidr_ipv4         = each.value
}

resource "aws_vpc_security_group_ingress_rule" "https" {
  for_each = toset(var.allowed_cidrs)

  security_group_id = aws_security_group.web.id
  description       = "HTTPS"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv4         = each.value
}

# サーバーから外への通信(パッケージ取得、SSM、Let's Encrypt など)はすべて許可
# 除外の理由: NAT Gateway や VPC エンドポイントを使わない構成(課金を避ける)なので、宛先を絞れない。
# 受信は allowed_cidrs と DB 用 SG で絞っている。
#trivy:ignore:AWS-0104
resource "aws_vpc_security_group_egress_rule" "all" {
  security_group_id = aws_security_group.web.id
  description       = "All outbound"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}
