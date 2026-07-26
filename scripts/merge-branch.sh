#!/usr/bin/env bash
# Rebase a feature branch onto its base, run the tests, and only fast-forward if they pass.
#
# Every merge into develop here is a fast-forward, so the tree that lands is the POST-rebase
# tree — which is the one nothing was testing. A branch cut before some other branch landed
# can be green on its own and red once rebased, with no textual conflict for git to report.
# Git's own pre-merge-commit hook cannot cover this: it only fires when a merge commit is
# created, and a fast-forward creates none. Hence a wrapper.
#
# Usage: ./scripts/merge-branch.sh [--e2e] [--base <branch>] [<branch>]
#   <branch>  Branch to merge; defaults to the current branch.
#   --base    Branch to rebase onto and merge into; defaults to develop.
#   --e2e     Also run the e2e suite (slow, drives the real UI).
#
# On test failure the rebased branch is left checked out so the failure can be fixed and the
# script re-run. Nothing is pushed.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

BASE_BRANCH="develop"
RUN_E2E=false
BRANCH=""

die() {
  echo "error: $1" >&2
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --e2e)  RUN_E2E=true; shift ;;
    --base) [[ $# -ge 2 ]] || die "--base needs a branch name"; BASE_BRANCH="$2"; shift 2 ;;
    -*)     die "unknown flag: $1" ;;
    *)      [[ -z "$BRANCH" ]] || die "only one branch may be given"; BRANCH="$1"; shift ;;
  esac
done

cd "$PROJECT_DIR"

if [[ -z "$BRANCH" ]]; then
  BRANCH="$(git rev-parse --abbrev-ref HEAD)"
fi

if [[ "$BRANCH" == "$BASE_BRANCH" ]]; then
  die "$BRANCH is the base branch — name the feature branch to merge"
fi

if [[ -n "$(git status --porcelain)" ]]; then
  die "working tree is not clean — commit or stash first"
fi

git rev-parse --verify --quiet "refs/heads/$BRANCH" >/dev/null \
  || die "no such branch: $BRANCH"
git rev-parse --verify --quiet "refs/heads/$BASE_BRANCH" >/dev/null \
  || die "no such base branch: $BASE_BRANCH"

echo "==> Rebasing $BRANCH onto $BASE_BRANCH"
git checkout --quiet "$BRANCH"

if ! git rebase "$BASE_BRANCH"; then
  echo
  echo "Rebase stopped with conflicts. Resolve them, finish with 'git rebase --continue'," >&2
  echo "then re-run this script. To bail out entirely: git rebase --abort" >&2
  exit 1
fi

# The point of the whole exercise: test the rebased tree, not the pre-rebase one.
echo "==> Testing the rebased tree"

if ! "$SCRIPT_DIR/test.sh" unit; then
  echo
  echo "Unit tests FAILED on the rebased tree. $BRANCH is left checked out and rebased," >&2
  echo "so you can fix the failures, commit, and re-run this script. Nothing was merged." >&2
  exit 1
fi

if [[ "$RUN_E2E" == true ]]; then
  if ! "$SCRIPT_DIR/test.sh" e2e; then
    echo
    echo "E2E tests FAILED on the rebased tree. $BRANCH is left checked out and rebased." >&2
    echo "Nothing was merged." >&2
    exit 1
  fi
fi

echo "==> Fast-forwarding $BASE_BRANCH to $BRANCH"
git checkout --quiet "$BASE_BRANCH"
git merge --ff-only "$BRANCH"

echo
echo "Merged $BRANCH into $BASE_BRANCH. Not pushed; branch not deleted."
