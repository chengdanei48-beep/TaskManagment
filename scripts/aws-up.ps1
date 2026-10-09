<#
.SYNOPSIS
  AWS のリソースを作る(使い始めるとき)。

.DESCRIPTION
  1. SSO のログインを確認(切れていればログインを促す)
  2. 今のPCの公開IPを terraform.tfvars の allowed_cidrs に反映
  3. terraform plan を表示
  4. 「yes」と入力したときだけ terraform apply

  使い終わったら aws-down.ps1 で消す(費用を抑えるため)。
  作り直すたびに公開IP(Elastic IP)は変わる。

.EXAMPLE
  .\scripts\aws-up.ps1
#>
[CmdletBinding()]
param(
  [string]$Profile = 'taskmgmt'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_aws-common.ps1')

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$tfDir = Join-Path $root 'infra\terraform'
$tfvars = Join-Path $tfDir 'terraform.tfvars'

Write-Host '== 1/4 ログイン確認 ==' -ForegroundColor Cyan
Assert-AwsLogin -Profile $Profile

# --- 公開IPを allowed_cidrs に反映 ---
Write-Host '== 2/4 接続元IPの確認 ==' -ForegroundColor Cyan
$ip = (curl.exe -s -4 --max-time 10 https://checkip.amazonaws.com).Trim()
if ($ip -notmatch '^\d{1,3}(\.\d{1,3}){3}$') { throw "公開IPを取得できません: '$ip'" }
$line = "allowed_cidrs = [`"$ip/32`"]"

if (-not (Test-Path $tfvars)) {
  throw "terraform.tfvars がありません。terraform.tfvars.example をコピーして budget_email を設定してください: $tfvars"
}
$content = Get-Content -Path $tfvars -Encoding UTF8
$current = $content | Where-Object { $_ -match '^\s*allowed_cidrs\s*=' } | Select-Object -First 1
if ($current -and $current.Trim() -eq $line) {
  Write-Host "  接続元IP: $ip (変更なし)"
} else {
  Write-Host "  接続元IP: $ip (terraform.tfvars を更新します。以前: $current)"
  $content = @($content | Where-Object { $_ -notmatch '^\s*allowed_cidrs\s*=' }) + $line
  [System.IO.File]::WriteAllLines($tfvars, $content, (New-Object System.Text.UTF8Encoding($false)))
}

# --- plan ---
Write-Host '== 3/4 terraform plan ==' -ForegroundColor Cyan
Push-Location $tfDir
try {
  Invoke-Native 'terraform' @('init', '-input=false')
  Invoke-Native 'terraform' @('plan', '-input=false', '-out', 'tfplan')

  # --- apply ---
  Write-Host '== 4/4 terraform apply ==' -ForegroundColor Cyan
  Write-Host '上の plan の内容で AWS にリソースを作ります。クレジットから費用が差し引かれます。' -ForegroundColor Yellow
  if (-not (Confirm-Yes '実行しますか?')) {
    Write-Host '中止しました。AWS には何も作っていません。'
    return
  }
  Invoke-Native 'terraform' @('apply', '-input=false', 'tfplan')
} finally {
  Remove-Item -Path (Join-Path $tfDir 'tfplan') -ErrorAction SilentlyContinue
  Pop-Location
}

Write-Host '完了。次: .\scripts\deploy.ps1 でアプリをデプロイ(RDS への接続確認だけなら .\scripts\check-rds.ps1)。使い終わったら .\scripts\aws-down.ps1 で消してください。' -ForegroundColor Green
