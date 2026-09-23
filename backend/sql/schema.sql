-- BahuBhashik v0 schema.
-- Run this in the Supabase SQL Editor (Dashboard > SQL Editor > New query).

create extension if not exists "pgcrypto";

-- v0 identity is a bare username. No passwords, no auth.
create table if not exists users (
  username   text primary key,
  -- Every language Sarvam can speak (bulbul:v3). Translation covers more, but a
  -- language with no voice can't be the output of a voice message.
  language   text not null check (language in (
    'bn-IN','en-IN','gu-IN','hi-IN','kn-IN','ml-IN','mr-IN','od-IN','pa-IN','ta-IN','te-IN'
  )),
  -- Which synthesized voice this person's messages are spoken in on the
  -- recipient's phone. A voice choice, not a claim about the speaker.
  voice      text not null default 'female' check (voice in ('female','male')),

  -- Four digits, stored in the clear at the owner's request. Gates the app's
  -- UI only: the message endpoints are deliberately unauthenticated in v0.
  -- Nullable so anyone who signed up before PINs existed is asked to set one
  -- instead of being locked out.
  pin        text check (pin is null or pin ~ '^[0-9]{4}$'),

  created_at timestamptz not null default now()
);

create table if not exists messages (
  id                    uuid primary key default gen_random_uuid(),
  sender                text not null references users(username) on delete cascade,
  -- Null for composed messages, which are translated for the sender to share
  -- outside the app rather than delivered to anyone in it.
  recipient             text references users(username) on delete cascade,
  kind                  text not null default 'community' check (kind in ('community','composed')),

  -- uploaded -> transcribing -> translating -> synthesizing -> ready | failed
  status                text not null default 'uploaded',

  -- Snapshotted at send time, NOT joined live: if someone changes their
  -- language setting later, old messages must keep describing what they were.
  source_lang           text not null,
  target_lang           text not null,

  original_audio_path   text,
  translated_audio_path text,
  source_text           text,
  translated_text       text,
  duration_seconds      int,
  error                 text,

  created_at            timestamptz not null default now(),
  updated_at            timestamptz not null default now(),

  constraint messages_recipient_matches_kind check ((kind = 'community') = (recipient is not null))
);

create index if not exists messages_recipient_idx on messages (recipient, created_at desc);
create index if not exists messages_pair_idx      on messages (sender, recipient, created_at desc);
create index if not exists messages_kind_idx      on messages (sender, kind, created_at desc);

-- Everything routes through the Node backend using the service-role key,
-- so RLS stays off in v0. Revisit if the client ever talks to Supabase directly.
