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
| `SHEETS_SPREADSHEET_ID` | id of the teacher's spreadsheet (the part of its URL between `/d/` and `/edit`) | empty; reading fails until it is set |
| `SHEETS_STUDENTS_TAB` | name of the tab that lists the students | `Students` |
| `AUTH_GOOGLE_CLIENT_ID` | the web OAuth client id; sign-in tokens are only accepted when issued for it | empty; every token is rejected until it is set |
| `APP_CACHE_STUDENTS_TTL` | how long the student list is kept | `5m` |
| `APP_CACHE_DECKS_TTL` | how long the decks of a student are kept | `15m` |
| `APP_CACHE_REFRESH_INTERVAL` | shortest time between two refreshes of a student | `10s` |

More variables (spreadsheet id, web client id, database connection) are added
by the cards that need them and listed here.

## Google Sheet

The teacher keeps the words in one spreadsheet, shared with the service account
as viewer. The service only reads it (`spreadsheets.readonly`).

- Tab `Students` (name configurable), first row `email | name | tab`: one row
  per student; `tab` is the name of that student's own tab.
- One tab per student, first row `english | hungarian | deck | example`: the
  `deck` column groups the words into decks (empty means the deck `Szavak`).
  A cell may hold several accepted answers separated by `;`; the first one is
  shown. `example` is optional.
- Columns are found by their header, case-insensitively, so their order does
  not matter. Rows without an English or a Hungarian word are skipped.

Authentication is the application default one. On Cloud Run that is the service
account the service runs as. Locally, impersonate the service account (you need
the Service Account Token Creator role on it) or point
`GOOGLE_APPLICATION_CREDENTIALS` at a key file kept outside the repository:

```
gcloud auth application-default login \
  --impersonate-service-account=<service account e-mail>
export SHEETS_SPREADSHEET_ID=<spreadsheet id>
mvn spring-boot:run
```

## Sign-in

Every endpoint except `/actuator/health` needs the Google ID token of the
student in the header `Authorization: Bearer <token>`. The service checks the
signature against Google's public keys and that the token

- is not expired,
- was issued by Google (`https://accounts.google.com`),
- was issued for the web client in `AUTH_GOOGLE_CLIENT_ID` (`aud`),
- has a verified e-mail address (`email_verified`).

The e-mail address is then looked up on the `Students` tab. The answers are:

| Case | Status | Body |
| --- | --- | --- |
| no, invalid, expired or foreign token | 401 | `{"code":"UNAUTHORIZED", ...}` |
| valid token, e-mail not on the student list | 403 | `{"code":"STUDENT_NOT_FOUND", ...}` |
| the spreadsheet cannot be read | 503 | `{"code":"SHEET_UNAVAILABLE", ...}` |

All errors have the form `{"code": "...", "message": "..."}`. The endpoints never
take a student id from the request: they work from the token, so a student can
only reach their own data. `GET /api/v1/me` returns the signed-in student's
`email` and `name`. In a controller the student is available as
`@AuthenticationPrincipal CurrentStudent student`.

## API

All endpoints need the sign-in token (see above) and live under `/api/v1`.

| Endpoint | Meaning |
| --- | --- |
| `GET /api/v1/me` | the signed-in student's `email` and `name` |
| `GET /api/v1/decks` | the student's decks with all their words |
| `POST /api/v1/decks/refresh` | reads the sheet again, answers like the `GET` |

`GET /api/v1/decks` answers a list of decks, sorted by name; the words keep the
order of the sheet:

```
[
  {
    "id": "…",
    "name": "Travel",
    "updatedAt": "2026-10-07T07:12:00Z",
    "words": [
      {
        "id": "…",
        "english": "boarding pass",
        "hungarian": "beszállókártya",
        "acceptedEnglish": ["boarding pass"],
        "acceptedHungarian": ["beszállókártya"],
        "example": null
      }
    ]
  }
]
```

`updatedAt` is when the backend last read the student's words from the sheet.
A student whose tab does not exist or is empty gets `[]`; when the sheet cannot
be read the answer is 503 `SHEET_UNAVAILABLE`.

### Cache and refresh

The sheet is not read on every request. The `Students` tab is kept for 5
minutes and the decks of a student for 15 minutes, so a change of the teacher
shows up in the app within that time. The durations can be set with
`app.cache.students-ttl`, `app.cache.decks-ttl` and `app.cache.refresh-interval`
(or the environment variables `APP_CACHE_STUDENTS_TTL`, `APP_CACHE_DECKS_TTL`
and `APP_CACHE_REFRESH_INTERVAL`, for example `10m`). `0s` switches a cache off.

`POST /api/v1/decks/refresh` throws away what is kept for the student, the
`Students` tab too, reads the sheet again and answers exactly like the `GET`.
To protect the sheet's quota a student can refresh once per 10 seconds; asking
more often returns the cached decks. If the sheet cannot be read during a
refresh but decks were cached, those are returned with their old `updatedAt`;
without cached decks the answer is 503 `SHEET_UNAVAILABLE`. A failed read is
never cached.

The cache lives in the memory of one instance; with several instances each has
its own.

The OpenAPI description is served at `/v3/api-docs`. Like everything else it
needs a token of a student on the list.

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
