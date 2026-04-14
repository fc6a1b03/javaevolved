# Repository Secrets

This document lists the GitHub repository secrets configured in **Settings → Secrets and variables → Actions**.

> **Note:** Secret values are never stored in the repository. They are managed exclusively through GitHub's encrypted secrets. See [GitHub docs](https://docs.github.com/en/actions/security-for-github-actions/security-guides/using-secrets-in-github-actions) for details.

## Secrets

| Secret | Purpose |
|--------|---------|
| `COPILOT_GITHUB_TOKEN` | GitHub token used by Copilot for automated workflows |
| `TWITTER_CONSUMER_KEY` | X (Twitter) API v2 OAuth 1.0a consumer key |
| `TWITTER_CONSUMER_KEY_SECRET` | X (Twitter) API v2 OAuth 1.0a consumer secret |
| `TWITTER_ACCESS_TOKEN` | X (Twitter) API v2 user access token |
| `TWITTER_ACCESS_TOKEN_SECRET` | X (Twitter) API v2 user access token secret |

## Usage

### `COPILOT_GITHUB_TOKEN`

Used by GitHub Copilot integrations. Not currently referenced in any workflow file.

### Twitter / X Secrets

Used for automated weekly social media posting of Java pattern updates to X (Twitter) via the X API v2.

- **Workflow:** [`.github/workflows/social-post.yml`](.github/workflows/social-post.yml) — runs every Monday at 14:00 UTC
- **Post script:** [`html-generators/socialpost.java`](html-generators/socialpost.java)
- **Spec:** [`specs/social-posts-spec.md`](specs/social-posts-spec.md)
