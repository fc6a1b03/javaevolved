# Automated Weekly Social Posts — Specification

## Problem
Post one pattern per week to X/Twitter, covering all 113+ patterns (~2.2 years of content). Fully automated via GitHub Actions with no manual steps.

## Approach
Use a **GitHub Actions scheduled workflow** that:
1. Reads a pre-shuffled queue file (`social/queue.txt`) listing all pattern keys
2. Each Monday, picks the next unposted pattern, posts to X/Twitter
3. Commits the updated state back to the repo to track progress
4. When all patterns are exhausted, reshuffles and starts over

### Why a queue file?
- Deterministic: you can review/reorder upcoming posts
- Resumable: survives workflow failures, repo changes
- Auditable: git history shows what was posted when

### Pre-drafted tweets
All tweet copy is pre-generated into `social/tweets.yaml` so it can be reviewed and edited before posting. The queue generator builds tweets from content YAML fields and validates they fit within 280 characters.

## Post Format
```
☕ {title}

{summary}

{oldLabel} → {modernLabel} (JDK {jdkVersion}+)

🔗 https://javaevolved.github.io/{category}/{slug}.html

#Java #JavaEvolved
```

## Implementation

### 1. Queue & Tweet Generator
**File:** `html-generators/generatesocialqueue.java`

JBang script that reads all content files, shuffles them, and writes:
- `social/queue.txt` — one `category/slug` per line (posting order)
- `social/tweets.yaml` — pre-drafted tweet text for each pattern, keyed by `category/slug`
- `social/state.yaml` — posting state (`currentIndex`, `lastPostedKey`, `lastTweetId`, `lastPostedAt`)

On re-run: detects new patterns (appends to end), prunes deleted patterns, preserves existing order and manual tweet edits. Use `--reshuffle` to force a full reshuffle.

### 2. Post Script
**File:** `html-generators/socialpost.java`

JBang script that:
- Reads state from `social/state.yaml`
- Looks up the pre-drafted tweet text from `social/tweets.yaml`
- Posts to X/Twitter via API v2 (OAuth 1.0a with HMAC-SHA1 signing)
- Updates state only after confirmed API success
- Supports `--dry-run` to preview without posting

### 3. GitHub Actions Workflow
**File:** `.github/workflows/social-post.yml`

- Schedule: every Monday at 14:00 UTC (10 AM ET)
- Manual dispatch support (`workflow_dispatch`)
- Concurrency group prevents double-posts
- Commits updated state back to repo

## Required GitHub Secrets
| Secret | Purpose |
|--------|---------|
| `TWITTER_CONSUMER_KEY` | X API v2 OAuth 1.0a consumer key |
| `TWITTER_CONSUMER_KEY_SECRET` | X API v2 OAuth 1.0a consumer secret |
| `TWITTER_ACCESS_TOKEN` | X API v2 user access token |
| `TWITTER_ACCESS_TOKEN_SECRET` | X API v2 user access token secret |

## Design Decisions
- **Twitter/X only** — Bluesky support can be added later
- **Text-only posts** with URL — platform unfurls the OG card automatically from `og:image` meta tags
- **Pre-drafted tweets** — generated into `social/tweets.yaml` for review; editable before posting
- **Random order** via pre-shuffled queue for variety across categories
- **Reshuffles** when all patterns are exhausted
- **JBang/Java for posting** — consistent with the rest of the project; safer for OAuth 1.0a signing than shell
- **State tracked** via `social/state.yaml` with `currentIndex`, `lastPostedKey`, `lastTweetId`, `lastPostedAt`
- **Social files in `social/`** (not `content/`) to avoid triggering site deploys
- **New patterns** appended to end of queue on re-run; deleted patterns pruned
- **Tweet length validation** — generator truncates summaries to fit 280 chars
