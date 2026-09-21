-- BahuBhashik v0 schema.
-- Run this in the Supabase SQL Editor (Dashboard > SQL Editor > New query).

create extension if not exists "pgcrypto";

-- v0 identity is a bare username. No passwords, no auth.
create table if not exists users (
  username   text primary key,
  language   text not null check (language in ('en-IN','hi-IN','mr-IN','gu-IN','kn-IN')),
  created_at timestamptz not null default now()
);

create table if not exists messages (
  id                    uuid primary key default gen_random_uuid(),
  sender                text not null references users(username) on delete cascade,
  recipient             text not null references users(username) on delete cascade,

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
  updated_at            timestamptz not null default now()
);

create index if not exists messages_recipient_idx on messages (recipient, created_at desc);
create index if not exists messages_pair_idx      on messages (sender, recipient, created_at desc);

-- Everything routes through the Node backend using the service-role key,
-- so RLS stays off in v0. Revisit if the client ever talks to Supabase directly.
