# BahuBhashik backend

Express + TypeScript. Owns every secret: the phone talks only to this server,
never to Supabase or Sarvam directly.

## Pipeline

```
recorded audio → Sarvam STT → Sarvam Translate → Sarvam TTS → stored audio
```

Source language comes from the sender's profile, target from the recipient's —
nobody picks a language when sending. Both are snapshotted onto the message so
later profile edits can't rewrite history.

Runs detached from the HTTP request (a 5-minute message takes minutes to
process) and reports progress through `messages.status`:

```
uploaded → transcribing → translating → synthesizing → ready
                                                     ↘ failed
```

## Setup

```bash
cp .env.example .env    # then fill it in
npm install
npm run dev
```

Uses Supabase's new `sb_secret_` API key (the legacy `service_role` JWT still
works via `SUPABASE_SERVICE_ROLE_KEY` if you have one).

Create the tables by pasting [`sql/schema.sql`](sql/schema.sql) into the Supabase
SQL Editor. The storage bucket is created automatically on first boot.

## Trying the pipeline without the app

The cheapest way to check translation quality, and the first thing to run:

```bash
npx tsx scripts/try-pipeline.ts sample.wav mr-IN kn-IN 20
```

Writes `sample.kn-IN.wav`, prints both transcripts and an estimated cost. The
trailing duration in seconds is optional but worth passing: at 28s or under it
uses the synchronous STT endpoint instead of the slower batch job.

## API

| Method | Path | Notes |
|---|---|---|
| `GET` | `/health` | |
| `GET` | `/users/languages` | the five supported languages |
| `POST` | `/users` | `{ username, language }` — create or log in |
| `GET` | `/users` | everyone, for the recipient picker |
| `POST` | `/messages` | multipart: `sender`, `recipient`, `audio`, `durationSeconds` |
| `GET` | `/messages?user=X` | inbox |
| `GET` | `/messages?user=X&with=Y` | one conversation, both directions |
| `GET` | `/messages/:id` | poll for status |
| `POST` | `/messages/:id/retry` | re-run a failure without re-recording |

Audio comes back as short-lived signed URLs, not paths.

## Model choices (measured, not assumed)

**Translation: `sarvam-translate:v1` (formal).** `mayura:v1` supports colloquial
registers, which sounds like the better fit for voice notes — but on real
Marathi→Kannada speech it leaves borrowed English in Latin script
("tree park", "doctor", "walk"), which Kannada TTS cannot pronounce.
`sarvam-translate:v1` returns fully native script. It also allows 2000 chars
per request against mayura's 1000, so it needs fewer chunks.
Note it rejects any `mode` other than `formal`, and rejects `output_script`
entirely.

**TTS: `bulbul:v3`.** Note v2's speaker names (`anushka`, `vidya`, ...) are
rejected by v3 — see `SPEAKERS` in `src/services/sarvam.ts` for valid ones.

## Costs

Roughly **₹25 per 5-minute message** (₹2.50 STT + ₹9 translate + ₹13.50 TTS) —
TTS dominates. Test with 30-second clips at ~₹2.50 instead.

## v0 limitations

- **No auth.** Typing an existing username signs you in as that person.
- **No notifications.** Clients poll.
- **Pipeline state is in-process.** A restart mid-run strands a message in a
  non-terminal status; `POST /messages/:id/retry` is the way out.
