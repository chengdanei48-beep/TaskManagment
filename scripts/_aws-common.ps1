# aws-up.ps1 / aws-down.ps1 の共通処理(直接は実行しない。ドット読み込みで使う)

function Invoke-Native {
  # ネイティブコマンドを実行し、失敗(終了コード != 0)したら止める
  param([string]$Exe, [string[]]$Arguments)
  & $Exe @Arguments
  if ($LASTEXITCODE -ne 0) { throw "$Exe が失敗しました(終了コード $LASTEXITCODE): $($Arguments -join ' ')" }
}

function Assert-AwsLogin {
  # SSO のログインが有効か確認し、切れていればログインを促す
  param([string]$AwsProfile)
  # Windows PowerShell 5.1 では、Stop 設定のまま native コマンドが stderr に出すと例外になる。
  # ここでは失敗を終了コードで判定したいので、この関数の中だけ Continue にする。
  $ErrorActionPreference = 'Continue'

  $null = aws sts get-caller-identity --profile $AwsProfile --query Account --output text 2>$null
  if ($LASTEXITCODE -eq 0) { return }

  Write-Host 'AWS のログインが切れています。ブラウザでログインしてください。' -ForegroundColor Yellow
  aws sso login --profile $AwsProfile
  $null = aws sts get-caller-identity --profile $AwsProfile --query Account --output text 2>$null
  if ($LASTEXITCODE -ne 0) { throw 'ログインできませんでした。aws sso login --profile taskmgmt を手動で実行して、エラーを確認してください。' }
}

function Confirm-Yes {
  # 「yes」と入力したときだけ true を返す
  param([string]$Message)
  $answer = Read-Host "$Message (yes/no)"
  return ($answer -eq 'yes')
}
