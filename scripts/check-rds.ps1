<#
.SYNOPSIS
  EC2 から RDS(PostgreSQL)に接続できるかを確認する。

.DESCRIPTION
  第2段階(RDS)の動作確認用。
  1. SSM が Online になるまで待つ
  2. SSM Run Command で、サーバーから RDS に接続する
     (パスワードは SSM Parameter Store から取得。画面にもログにも出さない)
     postgres:17-alpine の psql で、バージョン・データベース名・接続元を問い合わせる
  3. 結果を表示する

  RDS は VPC の中からだけ届く(インターネットからは接続できない)ので、サーバー経由で確認する。

  前提: aws sso login 済み、terraform apply 済み(RDS の作成には10分ほどかかる)。

.EXAMPLE
  .\scripts\check-rds.ps1
#>
[CmdletBinding()]
param(
  [string]$Profile = 'taskmgmt',
  [string]$Region = 'ap-northeast-1'
)

$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$tfDir = Join-Path $root 'infra\terraform'

# --- Terraform の出力 ---
Push-Location $tfDir
try {
  $out = terraform output -json | ConvertFrom-Json
  if ($LASTEXITCODE -ne 0 -or -not $out.instance_id -or -not $out.db_endpoint) {
    throw 'terraform output を取得できません。terraform apply 済みか確認してください。'
  }
} finally {
  Pop-Location
}
$instanceId = $out.instance_id.value
$dbHost = $out.db_endpoint.value
$dbName = $out.db_name.value
$dbUser = $out.db_username.value
$passwordParam = $out.db_password_parameter.value

# --- 1. SSM が Online になるまで待つ ---
Write-Host '== 1/2 SSM の接続待ち ==' -ForegroundColor Cyan
$online = $false
for ($i = 1; $i -le 30; $i++) {
  $ping = aws ssm describe-instance-information `
    --filters "Key=InstanceIds,Values=$instanceId" `
    --query 'InstanceInformationList[0].PingStatus' --output text --profile $Profile
  if ($ping -eq 'Online') { $online = $true; break }
  Write-Host "  待機中($i/30): $ping"
  Start-Sleep -Seconds 10
}
if (-not $online) { throw 'SSM が Online になりません。docs/aws-deploy-guide.md の「SSM が Online にならないとき」を確認してください。' }

# --- サーバーで実行するシェル ---
$shell = @'
set -e
# 初期設定(Docker の導入)の完了を待つ(最大90秒)
timeout 90 cloud-init status --wait >/dev/null 2>&1 || true
PGPASSWORD=$(aws ssm get-parameter --name '__PARAM__' --with-decryption --region __REGION__ --query Parameter.Value --output text)
export PGPASSWORD
docker pull -q postgres:17-alpine >/dev/null
echo "--- target: __HOST__ ---"
docker run --rm -e PGPASSWORD postgres:17-alpine \
  psql -h '__HOST__' -U '__USER__' -d '__DB__' -v ON_ERROR_STOP=1 -t -A \
  -c "select 'version     : ' || version()" \
  -c "select 'database    : ' || current_database()" \
  -c "select 'user        : ' || current_user" \
  -c "select 'client addr : ' || inet_client_addr()" \
  -c "select 'ssl         : ' || case when ssl then 'on' else 'off' end from pg_stat_ssl where pid = pg_backend_pid()"
echo "--- connected ---"
'@
$shell = $shell.Replace('__PARAM__', $passwordParam).Replace('__REGION__', $Region).Replace('__HOST__', $dbHost).Replace('__USER__', $dbUser).Replace('__DB__', $dbName)

# --- 2. サーバーへ指示(SSM Run Command) ---
Write-Host '== 2/2 サーバーから RDS に接続 ==' -ForegroundColor Cyan
$lines = ($shell -replace "`r", '') -split "`n"
$paramFile = Join-Path ([System.IO.Path]::GetTempPath()) 'taskmgmt-check-rds-params.json'
$json = (@{ commands = $lines } | ConvertTo-Json -Compress)
# AWS CLI は paramfile を OS の文字コード(日本語 Windows では cp932)で読むため、
# 日本語を \uXXXX に置き換えて ASCII だけのファイルにする
$json = [regex]::Replace($json, '[^\x00-\x7F]', { param($m) '\u{0:x4}' -f [int][char]$m.Value })
[System.IO.File]::WriteAllText($paramFile, $json, (New-Object System.Text.ASCIIEncoding))

try {
  $commandId = aws ssm send-command `
    --instance-ids $instanceId `
    --document-name 'AWS-RunShellScript' `
    --parameters "file://$paramFile" `
    --query 'Command.CommandId' --output text --profile $Profile
  if ($LASTEXITCODE -ne 0) { throw 'aws ssm send-command が失敗しました。' }

  aws ssm wait command-executed --command-id $commandId --instance-id $instanceId --profile $Profile
  $waitOk = ($LASTEXITCODE -eq 0)
  $result = aws ssm get-command-invocation --command-id $commandId --instance-id $instanceId `
    --query '{Status:Status,Stdout:StandardOutputContent,Stderr:StandardErrorContent}' --output json --profile $Profile | ConvertFrom-Json
  Write-Host $result.Stdout
  if ($result.Stderr) { Write-Host '--- 標準エラー ---' -ForegroundColor DarkGray; Write-Host $result.Stderr }
  if (-not $waitOk) { throw 'RDS への接続に失敗しました。上の出力を確認してください。' }
} finally {
  Remove-Item -Path $paramFile -ErrorAction SilentlyContinue
}

Write-Host 'EC2 から RDS に接続できました。' -ForegroundColor Green
