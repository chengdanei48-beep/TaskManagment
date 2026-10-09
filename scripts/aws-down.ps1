<#
.SYNOPSIS
  AWS のリソースをすべて消す(使い終わったとき)。費用を止める。

.DESCRIPTION
  1. SSO のログインを確認(切れていればログインを促す)
  2. 消えるリソースを表示(terraform plan -destroy)
  3. 「yes」と入力したときだけ terraform destroy
  4. 消し残しがないか AWS に問い合わせて確認

  注意: サーバーの中のデータ(DB など)も消える。必要なら先にバックアップを取る。

.EXAMPLE
  .\scripts\aws-down.ps1
#>
[CmdletBinding()]
param(
  [string]$Profile = 'taskmgmt',
  [string]$Region = 'ap-northeast-1'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_aws-common.ps1')

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$tfDir = Join-Path $root 'infra\terraform'

Write-Host '== 1/4 ログイン確認 ==' -ForegroundColor Cyan
Assert-AwsLogin -Profile $Profile

Write-Host '== 2/4 消えるリソースの確認 ==' -ForegroundColor Cyan
Push-Location $tfDir
try {
  Invoke-Native 'terraform' @('init', '-input=false')
  Invoke-Native 'terraform' @('plan', '-destroy', '-input=false', '-out', 'tfplan')

  Write-Host '== 3/4 terraform destroy ==' -ForegroundColor Cyan
  Write-Host '上のリソースをすべて削除します。サーバーの中のデータも消えます。' -ForegroundColor Yellow
  if (-not (Confirm-Yes '削除しますか?')) {
    Write-Host '中止しました。何も削除していません。'
    return
  }
  Invoke-Native 'terraform' @('apply', '-input=false', 'tfplan')
} finally {
  Remove-Item -Path (Join-Path $tfDir 'tfplan') -ErrorAction SilentlyContinue
  Pop-Location
}

# --- 消し残しの確認(Project=taskmgmt のタグが付いたもの) ---
Write-Host '== 4/4 消し残しの確認 ==' -ForegroundColor Cyan
$instances = aws ec2 describe-instances --profile $Profile --region $Region `
  --filters 'Name=tag:Project,Values=taskmgmt' 'Name=instance-state-name,Values=pending,running,stopping,stopped' `
  --query 'Reservations[].Instances[].InstanceId' --output text
$addresses = aws ec2 describe-addresses --profile $Profile --region $Region `
  --filters 'Name=tag:Project,Values=taskmgmt' --query 'Addresses[].AllocationId' --output text
$groups = aws ec2 describe-security-groups --profile $Profile --region $Region `
  --filters 'Name=tag:Project,Values=taskmgmt' --query 'SecurityGroups[].GroupId' --output text

$databases = aws rds describe-db-instances --profile $Profile --region $Region `
  --query "DBInstances[?starts_with(DBInstanceIdentifier, 'taskmgmt')].DBInstanceIdentifier" --output text
$snapshots = aws rds describe-db-snapshots --profile $Profile --region $Region `
  --query "DBSnapshots[?starts_with(DBInstanceIdentifier, 'taskmgmt')].DBSnapshotIdentifier" --output text

"  EC2 インスタンス : $(if ($instances) { $instances } else { 'なし' })"
"  Elastic IP       : $(if ($addresses) { $addresses } else { 'なし' })"
"  セキュリティグループ: $(if ($groups) { $groups } else { 'なし' })"
"  RDS              : $(if ($databases) { $databases } else { 'なし' })"
"  RDS スナップショット: $(if ($snapshots) { $snapshots } else { 'なし' })"

if ($instances -or $addresses -or $groups -or $databases -or $snapshots) {
  Write-Host '消し残しがあります。上のIDをAIに伝えて確認してください。' -ForegroundColor Red
} else {
  Write-Host 'すべて削除されました。課金は止まっています。' -ForegroundColor Green
}
