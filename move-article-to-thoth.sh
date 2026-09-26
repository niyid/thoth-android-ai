#!/usr/bin/env bash
#
# move-article-to-thoth.sh
#
# 1. Moves ai-assisted-software-development.html (and any local assets it
#    references) from ~/git/kabu-kabu-p2p to ~/thoth-android-ai.
# 2. Makes thoth-android-ai public and enables GitHub Pages (web-browsable)
#    via gh.
# 3. Commits and pushes both repositories.
#
# Run this from wherever you like; it cd's into each repo itself.

set -euo pipefail

SRC_REPO="${SRC_REPO:-$HOME/git/kabu-kabu-p2p}"
DST_REPO="${DST_REPO:-$HOME/thoth-android-ai}"
ARTICLE="ai-assisted-software-development.html"

if [[ ! -f "$SRC_REPO/$ARTICLE" ]]; then
    echo "ERROR: $SRC_REPO/$ARTICLE not found." >&2
    exit 1
fi

echo "=== Source repo: $SRC_REPO"
echo "=== Dest repo:   $DST_REPO"
echo

cd "$SRC_REPO"

# ---------------------------------------------------------------------------
# 1. Discover local assets referenced by the article.
#    We grep for src="...", href="...", and url(...) references, keep only
#    relative (non-http, non-data-uri, non-anchor) paths, and confirm they
#    exist on disk relative to the article.
# ---------------------------------------------------------------------------
echo "--- Scanning $ARTICLE for local asset references..."

mapfile -t CANDIDATES < <(
    grep -oE '(src|href)=["'"'"']([^"'"'"']+)["'"'"']|url\(([^)]+)\)' "$ARTICLE" \
    | sed -E 's/.*=["'"'"']//; s/["'"'"']$//; s/^url\(//; s/\)$//; s/^["'"'"']//; s/["'"'"']$//' \
    | sed 's/[#?].*//' \
    | grep -vE '^(https?:)?//' \
    | grep -vE '^data:' \
    | grep -vE '^#' \
    | grep -vE '^mailto:' \
    | sort -u
)

ASSETS=()
for c in "${CANDIDATES[@]:-}"; do
    [[ -z "$c" ]] && continue
    # normalize ./ prefix
    c="${c#./}"
    if [[ -f "$SRC_REPO/$c" ]]; then
        ASSETS+=("$c")
    fi
done

echo "Article: $ARTICLE"
if [[ ${#ASSETS[@]} -eq 0 ]]; then
    echo "No local asset files found referenced inside the article."
else
    echo "Associated local files found:"
    printf '  - %s\n' "${ASSETS[@]}"
fi
echo

read -rp "Proceed with moving the file(s) above from kabu-kabu-p2p to thoth-android-ai? [y/N] " CONFIRM
if [[ "$CONFIRM" != "y" && "$CONFIRM" != "Y" ]]; then
    echo "Aborted."
    exit 1
fi

# ---------------------------------------------------------------------------
# 2. Move article + assets, preserving relative directory structure.
# ---------------------------------------------------------------------------
ALL_FILES=("$ARTICLE" "${ASSETS[@]:-}")

for f in "${ALL_FILES[@]}"; do
    [[ -z "$f" ]] && continue
    DEST_PATH="$DST_REPO/$f"
    mkdir -p "$(dirname "$DEST_PATH")"
    echo "Moving $f -> $DEST_PATH"
    mv "$SRC_REPO/$f" "$DEST_PATH"
done

# ---------------------------------------------------------------------------
# 3. Commit + push the source repo (removal).
# ---------------------------------------------------------------------------
cd "$SRC_REPO"
git add -A -- "$ARTICLE" "${ASSETS[@]:-}" 2>/dev/null || git add -A
if ! git diff --cached --quiet; then
    git commit -m "Move ${ARTICLE} and associated assets to thoth-android-ai"
    git push
else
    echo "Nothing to commit in $SRC_REPO (unexpected)."
fi

# ---------------------------------------------------------------------------
# 4. Commit + push the destination repo (addition).
# ---------------------------------------------------------------------------
cd "$DST_REPO"
git add -A -- "$ARTICLE" "${ASSETS[@]:-}" 2>/dev/null || git add -A
if ! git diff --cached --quiet; then
    git commit -m "Add ${ARTICLE} and associated assets moved from kabu-kabu-p2p"
    git push
else
    echo "Nothing to commit in $DST_REPO (unexpected)."
fi

echo
echo "=== Article and $(( ${#ASSETS[@]} )) associated file(s) moved, committed, and pushed."

# ---------------------------------------------------------------------------
# 5. Make thoth-android-ai public and enable GitHub Pages (web-browsable).
# ---------------------------------------------------------------------------
echo
echo "--- Enabling public visibility + GitHub Pages for thoth-android-ai..."

if ! command -v gh >/dev/null 2>&1; then
    echo "WARNING: gh CLI not found; skipping Pages setup. Install it from https://cli.github.com/ and re-run just that step." >&2
else
    cd "$DST_REPO"

    NWO="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
    DEFAULT_BRANCH="$(gh repo view --json defaultBranchRef -q .defaultBranchRef.name)"
    echo "Target repo: $NWO (default branch: $DEFAULT_BRANCH)"

    CURRENT_VIS="$(gh repo view --json visibility -q .visibility)"
    if [[ "$CURRENT_VIS" != "PUBLIC" ]]; then
        echo "Setting repo visibility to public..."
        gh repo edit "$NWO" --visibility public --accept-visibility-change-consequences
    else
        echo "Repo is already public."
    fi

    echo "Enabling GitHub Pages from ${DEFAULT_BRANCH}:/ ..."
    if gh api -X POST "repos/${NWO}/pages" \
        -f "source[branch]=${DEFAULT_BRANCH}" \
        -f "source[path]=/" >/tmp/gh_pages_enable.json 2>/tmp/gh_pages_enable.err; then
        echo "Pages enabled."
    else
        if grep -q "already exists" /tmp/gh_pages_enable.err 2>/dev/null; then
            echo "Pages already exists for this repo — updating source instead..."
            gh api -X PUT "repos/${NWO}/pages" \
                -f "source[branch]=${DEFAULT_BRANCH}" \
                -f "source[path]=/"
        else
            echo "WARNING: Failed to enable Pages:" >&2
            cat /tmp/gh_pages_enable.err >&2
        fi
    fi

    echo
    echo "Current Pages status:"
    gh api "repos/${NWO}/pages" 2>/dev/null | grep -E '"html_url"|"status"' || true
fi

echo
echo "=== Done."
