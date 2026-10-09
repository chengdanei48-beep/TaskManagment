@{
  # Invoke-ScriptAnalyzer -Path scripts -Settings scripts/PSScriptAnalyzerSettings.psd1
  # CI(.github/workflows/ci.yml の powershell ジョブ)とローカルで同じ設定を使う。
  Severity     = @('Error', 'Warning')
  ExcludeRules = @(
    # 対話的に実行する運用スクリプトで、色つきの案内表示が目的(パイプラインに流す出力ではない)
    'PSAvoidUsingWriteHost'
    # Confirm-Yes の "Yes" は複数形ではない(誤検知)
    'PSUseSingularNouns'
  )
}
