# Object storage

The four JSON documents beside this file are the bucket's configuration. JSON
carries no comments, so the reasoning is here.

Nothing in the application changes to use them. `S3FileStore`, `StorageConfig`
and the SDK's default credential chain were built in phase six and hardened in
phase ten; this phase supplies the bucket they were waiting for. Replace
`REPLACE-WITH-BUCKET-NAME` and `REPLACE-WITH-PUBLIC-HOSTNAME` throughout before
applying anything.

---

## What the application actually does to the bucket

Three operations, and no others: `PutObject` on upload, `GetObject` when a
download is streamed or when `AttachmentRescan` reads a file back for scanning,
and `DeleteObject` when `AttachmentBytePurge` reclaims a soft-deleted file.
There is no `ListBucket` anywhere, because nothing enumerates the bucket — the
database is the index, and the key is built from identifiers the database
already holds.

`iam-policy.json` grants exactly those three. It is worth keeping it that way:
an application that cannot list a bucket cannot be made to leak the shape of
one.

---

## Credentials: there are none, and that is the design

`StorageConfig` builds the client on `DefaultCredentialsProvider` and there is
deliberately no property for a key or a secret. The chain reads a container role
(IRSA on EKS, a task role on ECS), then an instance profile, then the
environment.

Attach `iam-policy.json` to that role. Do **not** set `AWS_ACCESS_KEY_ID` and
`AWS_SECRET_ACCESS_KEY` unless the host genuinely has no role available — a key
pair is a long-lived secret that is rotated by redeploying, which is how
rotation stops happening.

On a plain EC2 host running the compose stack, the instance profile is the
mechanism: the SDK reaches the instance metadata service from inside the
container with no configuration at all.

---

## Encryption, and the trap in the obvious bucket policy

Turn on **default bucket encryption** (SSE-S3 is enough; SSE-KMS if a key
policy is wanted). Then every object is encrypted at rest whether or not the
request said anything about encryption.

**Do not add the `s3:x-amz-server-side-encryption` deny statement** that appears
in most hardening guides. `S3FileStore.put` sends `PutObjectRequest` with a
bucket, key, content type and content length and no encryption header, because
it does not need one when the bucket has a default. A policy that denies
requests lacking that header would refuse every upload the application makes,
and the failure would arrive as a 503 on somebody's first attachment rather than
at deployment time.

`bucket-policy.json` therefore carries one statement: refuse anything that is
not over TLS. That one is safe because the SDK has used HTTPS by default for
years, and it closes the case of somebody later configuring a plaintext
endpoint.

Alongside it, set **Block Public Access to all four settings on**. Downloads
never rely on public objects: the application answers with a presigned URL whose
lifetime is `ATTACHMENT_URL_TTL`, five minutes by default.

---

## Versioning is not optional here

Turn versioning **on**, and this is the one setting worth arguing for.

`AttachmentBytePurge` deletes the only copy of a file. It is careful — the
object goes before the row, it is off unless a deployment switches it on, and it
waits `ATTACHMENT_PURGE_RETENTION` (thirty days) after the soft delete — but it
is still a scheduled job whose job is deletion. Versioning means a mistake in it,
or a mistake in the retention setting, is recoverable.

It also matters for restores. The database can be restored to a point in time;
the bucket cannot follow it backwards unless the versions are still there. See
the "restoring the database and the bucket together" section of
`docs/database.md`, which is the failure mode this setting exists for.

`lifecycle.json` then bounds the cost: noncurrent versions expire after 35 days,
which is deliberately longer than the 30-day backup retention, so every version
a restorable backup could refer to still exists. Shorten one and shorten the
other, or the two stop agreeing.

The second rule aborts incomplete multipart uploads after a week. Uploads here
are 10MB at most and never multipart, so it should never fire; it is there
because an unnoticed incomplete upload is billed indefinitely.

---

## CORS, and why it is needed at all

A download looks like a redirect but behaves like a cross-origin fetch.

`downloadAttachment` in the frontend calls the API through the shared client so
the bearer token is attached — a plain anchor could not, because the token is
held in memory and never on a URL. The API answers `302` to a presigned S3 URL,
and `fetch` follows that redirect to the bucket. That second request is
cross-origin, carries an `Origin` header, and is refused by the browser unless
the bucket says otherwise.

So `s3-cors.json` allows `GET` from the public hostname and nothing else. Two
things have to agree for a download to work, and it is worth knowing both when
one breaks:

| If this is missing                                     | The symptom                                      |
| ------------------------------------------------------ | ------------------------------------------------ |
| The bucket CORS rule                                    | A CORS error in the browser console on download  |
| `APP_S3_ORIGIN` in the edge proxy's `connect-src`       | A Content-Security-Policy violation, same moment |

`docs/troubleshooting.md` has both.

---

## Applying it

```bash
BUCKET=your-bucket-name
REGION=eu-west-1

aws s3api create-bucket --bucket "$BUCKET" --region "$REGION" \
    --create-bucket-configuration LocationConstraint="$REGION"

aws s3api put-public-access-block --bucket "$BUCKET" \
    --public-access-block-configuration \
    "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"

aws s3api put-bucket-encryption --bucket "$BUCKET" \
    --server-side-encryption-configuration \
    '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'

aws s3api put-bucket-versioning --bucket "$BUCKET" \
    --versioning-configuration Status=Enabled

aws s3api put-bucket-policy     --bucket "$BUCKET" --policy file://bucket-policy.json
aws s3api put-bucket-cors       --bucket "$BUCKET" --cors-configuration file://s3-cors.json
aws s3api put-bucket-lifecycle-configuration --bucket "$BUCKET" --lifecycle-configuration file://lifecycle.json
```

Then set `STORAGE_BUCKET` and `STORAGE_REGION` in the environment. `StorageConfig`
refuses to start without either and names the one that is missing, so a mistake
here fails the deployment rather than the first upload.

---

## Anything that is not Amazon

`STORAGE_ENDPOINT` and `STORAGE_PATH_STYLE=true` point the same code at MinIO,
Cloudflare R2 or DigitalOcean Spaces. The IAM and bucket policies above are
Amazon's dialect and will need translating; the CORS rule and the reasoning
behind versioning apply unchanged.
