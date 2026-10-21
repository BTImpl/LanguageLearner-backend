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

The service needs a PostgreSQL. For local runs start the one of
`docker-compose.yml` (it accepts connections from this computer only, without
a password):

```
docker compose up -d
mvn spring-boot:run
```

The schema is built by Flyway at startup. To start from scratch:
`docker compose down -v`.

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

The tests that start the application, and the repository tests, run against a
real PostgreSQL in a container (Testcontainers), so **Docker must be running**
when you run `mvn verify`. GitHub's runners have it.

## Docker

```
docker build -t languagelearner-backend .
docker run --rm -p 8080:8080 -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/languagelearner languagelearner-backend
```

(with the database of `docker compose` running)

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
| `AUTH_GOOGLE_CLIENT_ID` | the OAuth client ids of the app, the web and the iOS client, separated by `;`; sign-in tokens are only accepted when issued for one of them | empty; every token is rejected until it is set |
| `GEMINI_API_KEY` | key of the Gemini API that reads the photos of worksheets (a billing-enabled project in production, see [Quiz extraction](#quiz-extraction)); never commit it | empty; reading a photo fails with `ExtractionUnavailableException` until it is set |
| `GEMINI_MODEL` | the pinned Gemini model | `gemini-3.5-flash-lite` |
| `GEMINI_TIMEOUT` | the longest a Gemini request may take | `60s` |
| `DATABASE_URL` | JDBC url of the PostgreSQL | `jdbc:postgresql://localhost:5432/languagelearner` (the local compose database) |
| `DATABASE_USERNAME` | database user | `languagelearner` |
| `DATABASE_PASSWORD` | database password | empty; set it for every real database |
| `APP_CACHE_STUDENTS_TTL` | how long the student list is kept | `5m` |
| `APP_CACHE_DECKS_TTL` | how long the decks of a student are kept | `15m` |
| `APP_CACHE_REFRESH_INTERVAL` | shortest time between two refreshes of a student | `10s` |

More variables (spreadsheet id, web client id, database connection) are added
by the cards that need them and listed here.

## Deployment

The service runs on Cloud Run with a free Neon PostgreSQL, and every change
that reaches `main` is deployed automatically by
`.github/workflows/deploy.yml`. The one-time setup (Artifact Registry,
Workload Identity Federation, secrets, the warm-up ping), how to check a
deployment and how to **roll back** to an earlier version are in
[docs/deployment.md](docs/deployment.md).

`/actuator/health` includes the database; `/actuator/health/liveness` does
not, and is what the scheduled ping calls.

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
- was issued for one of the clients in `AUTH_GOOGLE_CLIENT_ID` (`aud`; a native iOS sign-in
  issues the token for the iOS client, a web or Android sign-in for the web client),
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
| `POST /api/v1/attempts` | uploads a batch of answers |

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

### Uploading answers

`POST /api/v1/attempts` stores the answers of the signed-in student:

```
{
  "attempts": [
    {
      "id": "6f1c2a52-6b0e-4f7e-9a53-0d4c2f8b7a10",
      "deckId": "…",
      "wordId": "…",
      "mode": "QUIZ",
      "outcome": "FIRST_TRY",
      "answeredAt": "2026-10-07T12:03:11Z"
    }
  ]
}
```

`deckId` and `wordId` are the ids of `GET /api/v1/decks`; `mode` is one of
`FLASHCARDS`, `MATCHING`, `QUIZ`, `TYPING`, `MIX` and `outcome` one of
`FIRST_TRY`, `WITH_HELP`, `WRONG_FIRST`. The answer is `{"accepted": 2,
"duplicates": 0}`.

- **Idempotent.** The app generates the `id`. An answer whose id is stored
  already is skipped and counted in `duplicates`, so after a network error the
  app can simply send the same batch again.
- **Snapshot.** The server copies the deck name and the English and Hungarian
  words into the answer from the student's current decks. An answer about a
  deck or word that is not in them (any more) is stored with empty copies.
- **Limits.** At most 500 answers per request (an empty list is fine); every
  field is required; an unknown `mode` or `outcome`, a bad id or time is a 400.
- **Errors.** 503 `SHEET_UNAVAILABLE` when the decks cannot be read for the
  snapshot, 503 `DATABASE_UNAVAILABLE` when the database is down. Nothing is
  lost: the app keeps the answers and sends them again later. The batch is
  stored in one transaction.

### Quiz from a photo

`POST /api/v1/quizzes/from-image` reads the multiple-choice questions of a
photographed worksheet (see [Quiz extraction](#quiz-extraction)). Send a
`multipart/form-data` request with the photo in the `image` field:

```
curl -H "Authorization: Bearer $ID_TOKEN" -F image=@sheet.jpg \
  http://localhost:8080/api/v1/quizzes/from-image
```

```
{
  "title": "Unit 5 - Travel",
  "questions": [
    {
      "number": 1,
      "text": "She ___ to the airport yesterday.",
      "options": [{"label": "a", "text": "go"}, {"label": "b", "text": "went"}]
    }
  ],
  "skippedQuestions": 0
}
```

`title` is `null` when the sheet has no heading of its own; `skippedQuestions`
counts the incomplete questions that were left out. No option is marked as
correct: the questions are only copied, never answered.

- **Image.** JPEG or PNG, at most 8 MB. The type is read from the first bytes of
  the file, not from the declared `Content-Type`. The photo is only kept in
  memory while the request runs; nothing is stored and the database is not
  touched. The log has the size, the number of questions and the duration, never
  the image or the question texts.
- **Errors** (all with the usual `{"code": ..., "message": ...}` body): 400
  `IMAGE_MISSING` (no or empty `image` field, or not a multipart request), 413
  `IMAGE_TOO_LARGE`, 415 `IMAGE_TYPE_UNSUPPORTED`, 422 `NO_QUESTIONS_FOUND` (not
  a single question could be read completely), 503 `EXTRACTION_UNAVAILABLE` (the
  model is not set up, over its limit, blocked, unreachable or too slow; trying
  again later may work), 401 without a token and 403 for an account that is not
  on the student list.
- **Time.** A call takes about 10-20 seconds. The request to the model times out
  after `GEMINI_TIMEOUT` (60 seconds).

The OpenAPI description is served at `/v3/api-docs`. Like everything else it
needs a token of a student on the list.

## Quiz extraction

`QuizExtractor` turns the photo of a printed multiple-choice worksheet into an
`ExtractedQuiz` (title, numbered questions, labelled options). The rest of the
app only knows the interface; `GeminiQuizExtractor` is the implementation, so a
test can replace it and the model can be changed later.

- **The call.** The photo goes to the Gemini `generateContent` endpoint as inline
  data with a Spring `RestClient`; the key is sent in the `x-goog-api-key` header,
  never in the URL. The answer is bound to a JSON schema, so it is a structure and
  not free text. The model is pinned (`GEMINI_MODEL`, `gemini-3.5-flash-lite` as in
  the BE-12 experiment), not an alias like `-latest`.
- **The prompt** is in `src/main/resources/quiz/extraction-prompt.txt`. It only
  allows copying the questions: answering them, marking or suggesting the correct
  option is forbidden, and what cannot be read is left out, never guessed. A test
  checks that the prompt keeps saying so.
- **Validation.** The answer is checked even though it follows a schema: a question
  needs a number, a text and 2 to 5 options with a label and a text. An incomplete
  question is dropped and counted in `droppedQuestions`, never repaired.
- **Errors.** No usable question at all is a `NoQuestionsFoundException`. Everything
  that stops the model from being used (no key, too many requests or quota used up,
  HTTP errors, a request blocked for safety, a cut-off or unusable answer, a timeout)
  is an `ExtractionUnavailableException`; the endpoint of BE-14 turns it into a 503.
  The key is never part of a message or a log line.
- **Production.** The free AI Studio key is only for local experiments: the Gemini API
  terms require the paid service for apps made available to users in the EEA. The
  deployed service needs a billing-enabled project with a cost cap (BE-16).

## Database

PostgreSQL holds what the students answered; the words stay in the sheet.
Flyway applies the migrations in `src/main/resources/db/migration` at startup.
A migration that has been merged is never edited: a change is a new `V<n>__...`
file.

| Table | Content |
| --- | --- |
| `student` | `id`, `email` (unique, lower case), `name`, `created_at`. A student is stored the first time they make an authenticated request, and the name is refreshed from the sheet from time to time. If the database is down the request still goes on: the words come from the sheet alone |
| `attempt` | one answer: `id` (UUID, generated by the app, so uploading it twice keeps one row), `student_id`, a copy of the word (`deck_id`, `deck_name`, `word_id`, `english`, `hungarian`), `mode` (`FLASHCARDS`, `MATCHING`, `QUIZ`, `TYPING`, `MIX`), `outcome` (`FIRST_TRY`, `WITH_HELP`, `WRONG_FIRST`), `answered_at` (the phone's time), `received_at` (the server's time) |

The data is read and written with Spring's `JdbcClient` and plain SQL (in
`attempts`), not with an ORM: the upload must be an `INSERT ... ON CONFLICT DO
NOTHING`, which an entity repository does not give for client-generated ids.
`/actuator/health` includes the database, so it is `DOWN` while the database
cannot be reached.

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
