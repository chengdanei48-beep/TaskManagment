<#
.SYNOPSIS
  jar をビルドして AWS(EC2)にデプロイする。

.DESCRIPTION
  1. backend を画面同梱でビルド(./mvnw -Pbundle-frontend package)
  2. jar を S3 バケットへアップロード
  3. SSM Run Command でサーバーに「jar を取得して app を再起動」を指示
  4. https://<ホスト>/api/health が応答するまで待つ

  前提: aws sso login 済み、terraform apply 済み。
  注意: npm run dev を起動したままだとフロントのビルドが失敗することがある。止めてから実行する。

.EXAMPLE
  .\scripts\deploy.ps1
  .\scripts\deploy.ps1 -SkipBuild    # すでにビルド済みの jar を使う
#>
[CmdletBinding()]
param(
  [string]$Profile = 'taskmgmt',
  [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$tfDir = Join-Path $root 'infra\terraform'
$backendDir = Join-Path $root 'backend'
$jar = Join-Path $backendDir 'target\backend-0.0.1-SNAPSHOT.jar'

function Invoke-Native {
  # ネイティブコマンドを実行し、失敗(終了コード != 0)したら止める
  param([string]$Exe, [string[]]$Arguments)
  & $Exe @Arguments
  if ($LASTEXITCODE -ne 0) { throw "$Exe が失敗しました(終了コード $LASTEXITCODE): $($Arguments -join ' ')" }
}

# --- Terraform の出力(バケット名・インスタンスID・URL)を取得 ---
Push-Location $tfDir
try {
  $out = terraform output -json | ConvertFrom-Json
  if ($LASTEXITCODE -ne 0 -or -not $out.instance_id) { throw 'terraform output を取得できません。terraform apply 済みか確認してください。' }
} finally {
  Pop-Location
}
if (-not $out.artifact_bucket) { throw 'このスクリプトは最終段階(アプリのデプロイ)用です。S3 などを含む段階のTerraformがまだ適用されていません。' }
$bucket = $out.artifact_bucket.value
$instanceId = $out.instance_id.value
$appUrl = $out.app_url.value

# --- 1. ビルド ---
if (-not $SkipBuild) {
  Write-Host '== 1/4 ビルド ==' -ForegroundColor Cyan
  Push-Location $backendDir
  try {
    # テストは CI と PR 前の ./mvnw verify で実施済みの前提(DB 不要でデプロイできるようにする)
    Invoke-Native '.\mvnw.cmd' @('-B', '-Pbundle-frontend', '-DskipTests', 'package')
  } finally {
    Pop-Location
  }
}
if (-not (Test-Path $jar)) { throw "jar が見つかりません: $jar" }

# --- 2. S3 へアップロード ---
Write-Host '== 2/4 S3 へアップロード ==' -ForegroundColor Cyan
Invoke-Native 'aws' @('s3', 'cp', $jar, "s3://$bucket/app.jar", '--profile', $Profile)

# --- 3. サーバーへ指示(SSM Run Command) ---
Write-Host '== 3/4 サーバーを更新 ==' -ForegroundColor Cyan
$commands = @(
  'set -e',
  "aws s3 cp s3://$bucket/app.jar /opt/app/app.jar",
  'cd /opt/app',
  'docker compose up -d --force-recreate app'
)
$paramFile = Join-Path ([System.IO.Path]::GetTempPath()) 'taskmgmt-ssm-params.json'
@{ commands = $commands } | ConvertTo-Json -Compress | Set-Content -Path $paramFile -Encoding ascii

try {
  $commandId = aws ssm send-command `
    --instance-ids $instanceId `
    --document-name 'AWS-RunShellScript' `
    --parameters "file://$paramFile" `
    --query 'Command.CommandId' --output text --profile $Profile
  if ($LASTEXITCODE -ne 0) { throw 'aws ssm send-command が失敗しました。' }

  # 指示が終わるまで待つ(失敗なら例外)
  aws ssm wait command-executed --command-id $commandId --instance-id $instanceId --profile $Profile
  if ($LASTEXITCODE -ne 0) {
    aws ssm get-command-invocation --command-id $commandId --instance-id $instanceId `
      --query '{Status:Status,Stdout:StandardOutputContent,Stderr:StandardErrorContent}' --profile $Profile
    throw 'サーバー上の更新に失敗しました。上の出力を確認してください。'
  }
} finally {
  Remove-Item -Path $paramFile -ErrorAction SilentlyContinue
}

# --- 4. ヘルスチェック ---
Write-Host '== 4/4 ヘルスチェック ==' -ForegroundColor Cyan
$healthUrl = "$appUrl/api/health"
# Windows PowerShell 5.1 は既定で古い TLS を使うことがあるため TLS 1.2 を指定する
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
if ($out.tls_internal.value) {
  # 自己署名証明書のため、このスクリプト内のヘルスチェックに限り証明書の検証を省略する
  Add-Type -TypeDefinition 'using System.Net; using System.Security.Cryptography.X509Certificates; public class TrustAll : ICertificatePolicy { public bool CheckValidationResult(ServicePoint s, X509Certificate c, WebRequest r, int p) { return true; } }'
  [System.Net.ServicePointManager]::CertificatePolicy = New-Object TrustAll
}
$ok = $false
for ($i = 1; $i -le 40; $i++) {
  try {
    $res = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 10
    if ($res.StatusCode -eq 200) { $ok = $true; break }
  } catch {
    Write-Host "  待機中($i/40): $($_.Exception.Message)"
  }
  Start-Sleep -Seconds 10
}
if (-not $ok) {
  throw "ヘルスチェックに失敗しました: $healthUrl (初回は証明書の取得に数分かかります。ガイド 9章を参照)"
}
Write-Host "デプロイ完了: $appUrl" -ForegroundColor Green
