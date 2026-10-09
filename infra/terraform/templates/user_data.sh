#!/bin/bash
# EC2 の初回起動時に1回だけ実行される初期設定(第1段階: swap と Docker の導入のみ)。
# ログ: /var/log/cloud-init-output.log
set -euxo pipefail

# メモリ1GBのサーバーが重くなったときのための swap(1GB)
if [ ! -f /swapfile ]; then
  dd if=/dev/zero of=/swapfile bs=1M count=1024
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# Docker
dnf install -y docker
systemctl enable --now docker
