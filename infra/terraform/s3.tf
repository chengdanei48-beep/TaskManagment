data "aws_caller_identity" "current" {}

# ビルドした jar の置き場所。アカウントIDを付けて世界で一意の名前にする。
# 除外の理由: AWS-0090(バージョニング)は、jar を毎回上書きする一時置き場で、履歴は不要。
#trivy:ignore:AWS-0090
resource "aws_s3_bucket" "artifacts" {
  bucket        = "${var.project_name}-artifacts-${data.aws_caller_identity.current.account_id}"
  force_destroy = true # terraform destroy のときに中身ごと消す
}

resource "aws_s3_bucket_public_access_block" "artifacts" {
  bucket                  = aws_s3_bucket.artifacts.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# HTTPS 以外のアクセスを拒否する(AWS CLI・EC2 からのアクセスは HTTPS なので影響しない)
data "aws_iam_policy_document" "artifacts_tls_only" {
  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.artifacts.arn, "${aws_s3_bucket.artifacts.arn}/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "artifacts" {
  bucket = aws_s3_bucket.artifacts.id
  policy = data.aws_iam_policy_document.artifacts_tls_only.json

  # 公開ブロックの設定と同時に更新すると競合することがあるため、先に公開ブロックを済ませる
  depends_on = [aws_s3_bucket_public_access_block.artifacts]
}

# 除外の理由: AWS-0132(顧客管理キー)は、キーの月額課金が発生する。一時的な jar なので、AWS 管理キー(SSE-S3)で足りる。
#trivy:ignore:AWS-0132
resource "aws_s3_bucket_server_side_encryption_configuration" "artifacts" {
  bucket = aws_s3_bucket.artifacts.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}
