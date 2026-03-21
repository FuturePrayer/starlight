#!/usr/bin/env bash
set -euo pipefail

commit_sha="${1:-}"
if [[ -z "${commit_sha}" ]]; then
  echo "Usage: $0 <commit-sha>" >&2
  exit 1
fi

git fetch --no-tags origin "+refs/heads/*:refs/remotes/origin/*"
branches="$(git branch -r --contains "${commit_sha}" | tr -d ' ')"

echo "Branches containing ${commit_sha}:"
echo "${branches}"

if grep -qx 'origin/main' <<< "${branches}" || grep -qx 'origin/master' <<< "${branches}"; then
  echo "Tag commit belongs to main/master."
  exit 0
fi

echo "Tag commit is not contained in origin/main or origin/master." >&2
exit 1

