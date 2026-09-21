# BahuBhashik

बहुभाषिक — *multilingual*.

Voice messages between people who don't share a language. You record in
Marathi; your friend hears it in Kannada. Nobody chooses a translation
setting, because the app already knows what language each person speaks.

Built for two specific people: my mother, who speaks Marathi, and her friend,
who speaks Kannada.

## How it works

```
record  ──▶  Sarvam STT  ──▶  Sarvam Translate  ──▶  Sarvam TTS  ──▶  play
            (sender's lang)                        (recipient's lang)
```

The source language comes from the sender's profile and the target from the
recipient's, both snapshotted onto the message when it's sent — so changing
your language setting later can't rewrite what old messages were.

Translation runs detached from the upload request (five minutes of audio takes
minutes to process) and reports progress through a status field the client
polls:

```
uploaded → transcribing → translating → synthesizing → ready
                                                     ↘ failed
```

The recipient gets the translated audio, the original recording, and both
transcripts. Keeping the original matters: the translation is a synthesized
voice, so hearing the sender's own voice is the part that makes it feel like
a message from a person.

## Languages

English, Hindi, Marathi, Gujarati, Kannada.

Constrained by text-to-speech, which is the narrowest link in the chain.
OpenAI's `gpt-realtime-translate` was the original plan and was dropped: it
synthesizes into 13 languages, and Hindi is the only Indic one among them.

## Layout

| | |
|---|---|
| [`shared/`](shared) | Compose Multiplatform UI, Ktor client, `expect`/`actual` audio |
| [`androidApp/`](androidApp) | Android entry point |
| [`iosApp/`](iosApp) | iOS entry point |
| [`backend/`](backend) | Express + TypeScript API and pipeline ([details](backend/README.md)) |

## Running it

**Backend** — needs a Supabase project and a Sarvam API key:

```bash
cd backend
cp .env.example .env    # then fill it in
npm install
npm run dev
```

Paste [`backend/sql/schema.sql`](backend/sql/schema.sql) into the Supabase SQL
editor. The storage bucket creates itself on first boot.

**Apps** — point `ServerConfig.baseUrl` at the backend, then:

```bash
./gradlew :androidApp:assembleDebug     # Android
open iosApp/iosApp.xcodeproj            # iOS
```

A phone can't reach `localhost` — use your machine's LAN address for local
testing, or the deployed URL.

## What v0 deliberately doesn't do

- **No authentication.** A username is an identity; typing an existing one
  signs you in as that person. Fine for three users who know each other.
- **No notifications.** Clients poll.
- **No message deletion**, no read state, no groups.
- **Pipeline state lives in the process.** A restart mid-translation strands
  a message; `POST /messages/:id/retry` is the way out.

## Cost

Around ₹25 per five-minute message — ₹2.50 transcription, ₹9 translation,
₹13.50 speech synthesis. Synthesis dominates, which is not where you'd
expect the money to go.
