# EC2 に与える「役割(ロール)」。必要最小限の権限だけ付ける。
data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "app" {
  name               = "${var.project_name}-app"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

# SSM Session Manager / Run Command でサーバーに接続・指示を送れるようにする(SSH の代わり)
resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.app.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# EC2 が jar(S3)と DB パスワード(SSM パラメータ)だけ読めるようにする
data "aws_iam_policy_document" "app_access" {
  statement {
    sid       = "ReadArtifacts"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.artifacts.arn}/*"]
  }

  statement {
    sid       = "ReadDbPassword"
    actions   = ["ssm:GetParameter"]
    resources = [aws_ssm_parameter.db_password.arn]
  }
}

resource "aws_iam_role_policy" "app_access" {
  name   = "${var.project_name}-app-access"
  role   = aws_iam_role.app.id
  policy = data.aws_iam_policy_document.app_access.json
}

resource "aws_iam_instance_profile" "app" {
  name = "${var.project_name}-app"
  role = aws_iam_role.app.name
}
