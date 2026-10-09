<#
.SYNOPSIS
  EC2 の動作確認ページを、サーバー上に一時的に立てる(または止める)。

.DESCRIPTION
  第1段階(EC2 のみ)の動作確認用。
  1. SSM が Online になるまで待つ
  2. SSM Run Command で、サーバーの状態(インスタンス情報・Docker・swap・ディスク等)を載せた
     HTML を作り、Caddy コンテナで 80 番に表示する
  3. このPCから http://<公開IP> に届くか確認し、URL を表示する
  ブラウザで URL を開いて確認する。終わったら -Stop で止める。

  前提: aws sso login 済み、terraform apply 済み。

.EXAMPLE
  .\scripts\check-ec2.ps1          # 確認ページを立てる
  .\scripts\check-ec2.ps1 -Stop    # 確認ページを止める
#>
[CmdletBinding()]
param(
  [string]$Profile = 'taskmgmt',
  [switch]$Stop
)

$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$tfDir = Join-Path $root 'infra\terraform'

# --- Terraform の出力 ---
Push-Location $tfDir
try {
  $out = terraform output -json | ConvertFrom-Json
  if ($LASTEXITCODE -ne 0 -or -not $out.instance_id) { throw 'terraform output を取得できません。terraform apply 済みか確認してください。' }
} finally {
  Pop-Location
}
$instanceId = $out.instance_id.value
$publicIp = $out.public_ip.value

# --- 1. SSM が Online になるまで待つ ---
Write-Host '== 1/3 SSM の接続待ち ==' -ForegroundColor Cyan
$online = $false
for ($i = 1; $i -le 30; $i++) {
  $ping = aws ssm describe-instance-information `
    --filters "Key=InstanceIds,Values=$instanceId" `
    --query 'InstanceInformationList[0].PingStatus' --output text --profile $Profile
  if ($ping -eq 'Online') { $online = $true; break }
  Write-Host "  待機中($i/30): $ping"
  Start-Sleep -Seconds 10
}
if (-not $online) { throw 'SSM が Online になりません。起動直後は数分かかります。時間をおいて再実行してください。' }

# --- サーバーで実行するシェル ---
if ($Stop) {
  $shell = 'docker rm -f check 2>/dev/null || true; echo stopped'
} else {
  $shell = @'
set -e
# 起動直後は初期設定(Docker の導入)の途中なので、終わるまで待つ(最大90秒。足りなければ再実行する)
timeout 90 cloud-init status --wait >/dev/null 2>&1 || true
mkdir -p /opt/check
TOKEN=$(curl -s -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60')
md() { curl -s -H "X-aws-ec2-metadata-token: $TOKEN" "http://169.254.169.254/latest/meta-data/$1"; }
cat > /opt/check/index.html <<HTML
<!doctype html>
<html lang="ja"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>EC2 動作確認</title>
<style>
body{font-family:system-ui,sans-serif;max-width:46rem;margin:2rem auto;padding:0 1rem;line-height:1.6}
h1{font-size:1.4rem} table{border-collapse:collapse;width:100%} th,td{border:1px solid #8884;padding:.4rem .6rem;text-align:left;vertical-align:top}
th{width:11rem} .ok{color:#0a7d33;font-weight:bold} pre{margin:0;white-space:pre-wrap}
</style></head><body>
<h1><span class="ok">&#10003;</span> EC2 に外から届いています</h1>
<p>このページが見えている = セキュリティグループ(80番)・Elastic IP・Docker・インターネット接続が動いています。</p>
<table>
<tr><th>あなたのIP(サーバーから見た)</th><td>{{.RemoteIP}}</td></tr>
<tr><th>インスタンスID</th><td>$(md instance-id)</td></tr>
<tr><th>インスタンスタイプ</th><td>$(md instance-type)</td></tr>
<tr><th>AZ</th><td>$(md placement/availability-zone)</td></tr>
<tr><th>公開IP</th><td>$(md public-ipv4)</td></tr>
<tr><th>OS</th><td>$(. /etc/os-release; echo "$PRETTY_NAME")</td></tr>
<tr><th>稼働時間</th><td>$(uptime -p)</td></tr>
<tr><th>Docker</th><td>$(docker --version)</td></tr>
<tr><th>メモリ</th><td><pre>$(free -m | sed -n 1,2p)</pre></td></tr>
<tr><th>swap</th><td><pre>$(free -m | sed -n 3p)</pre></td></tr>
<tr><th>ディスク(/)</th><td><pre>$(df -h / | sed -n 1,2p)</pre></td></tr>
<tr><th>初期設定(cloud-init)</th><td>$(cloud-init status 2>&1 | head -1)</td></tr>
</table>
<p>確認が済んだら <code>scripts\check-ec2.ps1 -Stop</code> でこのページを止めます。</p>
</body></html>
HTML
cat > /opt/check/Caddyfile <<'CADDY'
{
	auto_https off
}
:80 {
	root * /srv
	templates
	file_server
}
CADDY
docker rm -f check 2>/dev/null || true
docker run -d --name check -p 80:80 -v /opt/check:/srv:ro -v /opt/check/Caddyfile:/etc/caddy/Caddyfile:ro caddy:2 >/dev/null
echo started
'@
}

# --- 2. サーバーへ指示(SSM Run Command) ---
Write-Host '== 2/3 サーバーに指示 ==' -ForegroundColor Cyan
$lines = ($shell -replace "`r", '') -split "`n"
$paramFile = Join-Path ([System.IO.Path]::GetTempPath()) 'taskmgmt-check-params.json'
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
  aws ssm get-command-invocation --command-id $commandId --instance-id $instanceId `
    --query '{Status:Status,Stdout:StandardOutputContent,Stderr:StandardErrorContent}' --output json --profile $Profile
  if (-not $waitOk) { throw 'サーバー上の処理に失敗しました。上の出力を確認してください。' }
} finally {
  Remove-Item -Path $paramFile -ErrorAction SilentlyContinue
}

if ($Stop) {
  Write-Host '確認ページを止めました。' -ForegroundColor Green
  return
}

# --- 3. このPCから届くか確認 ---
Write-Host '== 3/3 このPCから接続確認 ==' -ForegroundColor Cyan
$url = "http://$publicIp"
$ok = $false
for ($i = 1; $i -le 12; $i++) {
  try {
    $res = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 10
    if ($res.StatusCode -eq 200) { $ok = $true; break }
  } catch {
    Write-Host "  待機中($i/12): $($_.Exception.Message)"
  }
  Start-Sleep -Seconds 5
}
if (-not $ok) { throw "このPCから $url に届きません。許可IP(allowed_cidrs)が今のIPと合っているか確認してください。" }
Write-Host "確認ページ: $url (ブラウザで開いてください)" -ForegroundColor Green
