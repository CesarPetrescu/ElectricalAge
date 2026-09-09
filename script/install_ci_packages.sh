#!/usr/bin/env bash
# These jobs need Ubuntu packages, not Chrome/Microsoft third-party repositories.
# Scope BOTH update and install; retain all normal archive signature/hash checks.
set -euo pipefail
if [[ $# -eq 0 ]]; then
  echo 'Usage: bash script/install_ci_packages.sh PACKAGE [PACKAGE ...]' >&2
  exit 2
fi
source_file="${ELN_CI_APT_SOURCE_FILE:-/etc/apt/sources.list.d/ubuntu.sources}"
if [[ ! -s "$source_file" || "$source_file" != /* ]]; then
  echo "Missing absolute Ubuntu sources file: $source_file" >&2
  exit 2
fi
for package in "$@"; do
  [[ "$package" =~ ^[a-z0-9][a-z0-9+.-]*(:[a-z0-9-]+)?$ ]] || {
    echo "Invalid package name: $package" >&2; exit 2;
  }
done
options=(-o "Dir::Etc::sourcelist=$source_file" -o 'Dir::Etc::sourceparts=-'
         -o 'Acquire::Retries=3' -o 'APT::Update::Error-Mode=any')
echo "Installing CI dependencies from $source_file (third-party sources excluded)"
sudo apt-get "${options[@]}" update
sudo apt-get "${options[@]}" install -y --no-install-recommends "$@"
