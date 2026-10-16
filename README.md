# LanguageLearner backend

[![CI](https://github.com/BTImpl/LanguageLearner-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/BTImpl/LanguageLearner-backend/actions/workflows/ci.yml)

Backend of the [LanguageLearner](https://github.com/BTImpl/LanguageLearner)
app. It reads the teacher's Google Sheet, serves the decks of the signed-in
student and stores the results of the practice rounds.

- Java 25 (LTS), Spring Boot 4, Maven. Use a JDK 25 locally: it is what the CI
  and the Docker image use
- Group `eu.btimpl`, artifact `languagelearner-backend`
- Packages by feature under `eu.btimpl.languagelearner`: `auth`, `sheets`,
  `decks`, `attempts`, `config`

## Running

```
mvn spring-boot:run
```

The service listens on the port in `PORT` (8080 by default). Health check, open
without authentication:

```
curl localhost:8080/actuator/health
```

## Testing and checks

```
mvn verify              # build and test
tool/format.sh check    # or 'tool/format.sh fix' to format
```

Formatting is google-java-format through Spotless. The CI runs exactly these
two commands.

## Docker

```
docker build -t languagelearner-backend .
docker run --rm -p 8080:8080 languagelearner-backend
```

The image is built in two stages and runs as a non-root user. `PORT` is
honoured, as Cloud Run requires.

## Configuration

Everything that differs per environment comes from environment variables;
`application.yml` only has defaults for local runs. No secret is ever committed.

| Variable | Meaning | Default |
| --- | --- | --- |
| `PORT` | port the service listens on | `8080` |

More variables (spreadsheet id, web client id, database connection) are added
by the cards that need them and listed here.

## Git hook

A pre-commit hook runs the formatting check before every commit, the same
check the CI runs. Enable it once per clone:

```
git config core.hooksPath .githooks
```

If it fails, run `tool/format.sh fix`, stage the changes and commit again.
The hook is skipped (with a warning) when `mvn` is not in the PATH, for
example when committing from a GUI; the CI still catches it.

## Branches and CI

`main` is protected: code gets in only through a pull request from a
`feature/<card id>_<short summary>` branch (for example
`feature/be-05_deck_api`), and only when the CI is green. The CI runs on every
pull request (so on every commit pushed to it) and on every push to `main`. It
has two jobs, both required:

- `verify`: formatting check, `mvn verify`
- `feature-branch-only`: fails when the source branch of the pull request is
  not a `feature/*` branch

Repository settings (Settings, Branches, rule for `main`):

- require a pull request before merging (0 approvals, the repository has one
  developer)
- require status checks to pass before merging: `verify` and
  `feature-branch-only`, with the branch up to date
- apply the rules to administrators too, no force pushes, no deletion

The same with the GitHub CLI:

```
gh api -X PUT repos/BTImpl/LanguageLearner-backend/branches/main/protection --input - <<'JSON'
{
  "required_status_checks": { "strict": true, "contexts": ["verify", "feature-branch-only"] },
  "enforce_admins": true,
  "required_pull_request_reviews": { "required_approving_review_count": 0 },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
JSON
```
