# Deployment

The backend runs on **Google Cloud Run** in `europe-west1` (Belgium), its
database is a free **Neon** PostgreSQL. Both are free at this size (two
students). Every change that reaches `main` is deployed by
`.github/workflows/deploy.yml`, with no human step.

```
GitHub Actions ──(Workload Identity Federation, no key)──▶ Artifact Registry ─▶ Cloud Run ──▶ Neon
Cloud Scheduler ──(ping every 5 minutes)──▶ /actuator/health/liveness
```

- The service is public; the protection is in the app (Google ID token, and the
  student list in the sheet).
- The service runs as the service account the teacher's sheet is shared with
  (card BE-02), so it reads the sheet with no key file.
- Minimum 0 instances (it sleeps when unused), startup CPU boost, 512 MiB, at
  most 2 instances.
- The Flyway migrations run when an instance starts.
- Settings come from environment variables; the three database values come
  from Secret Manager.

## One-time setup

Do this once, in this order. Shell variables used below (set them in the
terminal where you run the commands):

```
PROJECT_ID=<the Google Cloud project of BE-02>
REGION=europe-west1
RUNTIME_SA=<e-mail of the BE-02 service account>      # the one the sheet is shared with
REPO=BTImpl/LanguageLearner-backend
PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')
gcloud config set project "$PROJECT_ID"
```

### 1. Enable the APIs

```
gcloud services enable run.googleapis.com artifactregistry.googleapis.com \
  secretmanager.googleapis.com cloudscheduler.googleapis.com \
  iamcredentials.googleapis.com sts.googleapis.com
```

### 2. Artifact Registry

```
gcloud artifacts repositories create languagelearner \
  --repository-format=docker --location="$REGION" \
  --description="LanguageLearner backend images"
```

Keep the storage in the free tier by deleting old images (the five newest stay):

```
cat > /tmp/cleanup.json <<'JSON'
[
  { "name": "keep-recent", "action": { "type": "Keep" },
    "mostRecentVersions": { "keepCount": 5 } },
  { "name": "delete-the-rest", "action": { "type": "Delete" },
    "condition": { "tagState": "any" } }
]
JSON
gcloud artifacts repositories set-cleanup-policies languagelearner \
  --location="$REGION" --policy=/tmp/cleanup.json --no-dry-run
```

### 3. The Neon database

1. Sign up at <https://neon.com> (free plan, no card needed) and create a
   project. Region: **AWS Europe (Frankfurt)**, the closest to Belgium.
2. Open the project's **Connect** dialog. Turn **connection pooling off**
   (the host must not contain `-pooler`: Flyway uses advisory locks, which a
   pooler in transaction mode breaks).
3. Note the host, the database name, the user and the password.

The JDBC url is:

```
jdbc:postgresql://<host>/<database>?sslmode=require
```

Neon suspends an idle database after about 5 minutes and wakes it on the next
query, which costs about a second. That is why the ping below uses the
liveness endpoint, which does not touch the database: pinging the database
would keep it awake all month and use up the free compute hours.

### 4. Secrets

Copy the value, then create each secret from the clipboard (`pbpaste` is macOS;
this keeps the values out of the shell history):

```
# copy the JDBC url, then:
pbpaste | gcloud secrets create languagelearner-database-url --data-file=-
# copy the database user, then:
pbpaste | gcloud secrets create languagelearner-database-username --data-file=-
# copy the database password, then:
pbpaste | gcloud secrets create languagelearner-database-password --data-file=-
```

Let the service account read them:

```
for secret in url username password; do
  gcloud secrets add-iam-policy-binding "languagelearner-database-$secret" \
    --member="serviceAccount:$RUNTIME_SA" --role=roles/secretmanager.secretAccessor
done
```

### 5. The deploy service account

GitHub deploys as its own service account, separate from the one the service
runs as:

```
gcloud iam service-accounts create languagelearner-deployer \
  --display-name="LanguageLearner deployer (GitHub Actions)"
DEPLOY_SA="languagelearner-deployer@$PROJECT_ID.iam.gserviceaccount.com"

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$DEPLOY_SA" --role=roles/run.admin
gcloud artifacts repositories add-iam-policy-binding languagelearner \
  --location="$REGION" \
  --member="serviceAccount:$DEPLOY_SA" --role=roles/artifactregistry.writer
gcloud iam service-accounts add-iam-policy-binding "$RUNTIME_SA" \
  --member="serviceAccount:$DEPLOY_SA" --role=roles/iam.serviceAccountUser
```

If the first deployment complains about `serviceusage.services.use`, also grant
`roles/serviceusage.serviceUsageConsumer` on the project.

### 6. Workload Identity Federation (no key file)

This lets GitHub Actions of this repository, and only of its `main` branch,
act as the deploy service account:

```
gcloud iam workload-identity-pools create github \
  --location=global --display-name="GitHub Actions"

gcloud iam workload-identity-pools providers create-oidc github-actions \
  --location=global --workload-identity-pool=github \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository=='$REPO' && assertion.ref=='refs/heads/main'"

gcloud iam service-accounts add-iam-policy-binding "$DEPLOY_SA" \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/attribute.repository/$REPO"
```

### 7. Repository variables

None of these is a secret (a web client id is public by design), so they are
plain repository variables:

```
gh variable set GCP_PROJECT_ID --repo "$REPO" --body "$PROJECT_ID"
gh variable set GCP_DEPLOY_SERVICE_ACCOUNT --repo "$REPO" --body "$DEPLOY_SA"
gh variable set GCP_RUNTIME_SERVICE_ACCOUNT --repo "$REPO" --body "$RUNTIME_SA"
gh variable set GCP_WORKLOAD_IDENTITY_PROVIDER --repo "$REPO" \
  --body "projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/providers/github-actions"
gh variable set SHEETS_SPREADSHEET_ID --repo "$REPO" --body "<spreadsheet id>"
gh variable set AUTH_GOOGLE_CLIENT_ID --repo "$REPO" --body "<web OAuth client id>"
```

Until `GCP_PROJECT_ID` is set the deploy workflow is skipped.

### 8. First deployment

On GitHub: Actions, **Deploy**, **Run workflow** (branch `main`). The last step
of the workflow calls `/actuator/health/liveness` and `/actuator/health` of the
new revision and fails unless both answer `UP`. The second one also proves the
database connection and the migrations.

Afterwards get the address of the service:

```
gcloud run services describe languagelearner-backend --region "$REGION" --format='value(status.url)'
```

### 9. Keep it warm

A scheduled ping stops the first real request from waiting for a cold start:

```
URL=$(gcloud run services describe languagelearner-backend --region "$REGION" --format='value(status.url)')
gcloud scheduler jobs create http languagelearner-warm \
  --location="$REGION" --schedule="*/5 * * * *" \
  --uri="$URL/actuator/health/liveness" --http-method=GET --attempt-deadline=60s
```

(Cloud Scheduler gives 3 jobs per billing account for free. About 8,600 pings a
month are far below the free request quota of Cloud Run.)

### 10. Cost alert

Console, **Billing**, **Budgets & alerts**, create a budget of a few euros with
e-mail alerts at 50 % and 100 %. Nothing here should cost anything; the alert is
the safety net.

## Checking it works

```
curl "$URL/actuator/health"                  # {"status":"UP"}
curl -i "$URL/api/v1/decks"                  # 401, no token
curl -H "Authorization: Bearer $ID_TOKEN" "$URL/api/v1/decks"   # the student's decks
```

`$ID_TOKEN` is a Google ID token issued for the **web client id**. The app
(card LL-27) gets one when the student signs in. Before the app exists, one
can be fetched with the OAuth 2.0 Playground (gear icon: "Use your own OAuth
credentials" with the web client id and secret, scopes `openid email`; add
`https://developers.google.com/oauthplayground` as an authorized redirect URI
of that client first) and copying the `id_token` of the token exchange. The
token is valid for an hour.

Logs: `gcloud run services logs read languagelearner-backend --region "$REGION" --limit 50`
(plain text; a container that does not start is explained in the log of its
revision, which the failed deployment prints a link to).

## Rolling back to an earlier version

Every deployment is a **revision**; the image of each is tagged with the git
commit. Either:

1. **Revert in git** (the clean way): revert the change on `main` through a
   pull request; the deploy workflow ships the result. Or
2. **Switch the traffic right now**, without waiting for a build:

```
gcloud run revisions list --service languagelearner-backend --region "$REGION"
gcloud run services update-traffic languagelearner-backend --region "$REGION" \
  --to-revisions=<revision name>=100
```

Once traffic is pinned to a revision, later deployments do **not** get traffic
automatically. When the fixed version is on `main`, run

```
gcloud run services update-traffic languagelearner-backend --region "$REGION" --to-latest
```

A database migration is never rolled back by switching revisions: the older
code must work with the newer schema, so keep migrations backwards compatible
(add columns and tables, do not drop or rename in the same release).

## Limits and later changes

- **Cold start:** with 0 instances and a JVM the first request after a quiet
  period can take several seconds. The ping avoids most of it. If it still
  bothers, the next steps are a class-data-sharing archive for the image, then
  a native image; or `--min-instances 1`, which costs money.
- **Cache per instance:** the caches of the sheet data live in the memory of an
  instance (see the README); two instances have two caches.
- **Neon limits:** the free plan has a monthly compute allowance and a small
  storage limit. The data here is a few rows per answer; revisit when real
  numbers show up.
