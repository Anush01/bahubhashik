-- Migration for databases created before Compose and the wider language list.
-- New databases get all of this from schema.sql directly.
-- Safe to run more than once, and safe to run before the matching backend
-- deploy: nothing already live reads or writes the new column.

-- 1. Every language Sarvam can speak (bulbul:v3), not just the first five.
alter table users drop constraint if exists users_language_check;
alter table users add constraint users_language_check check (language in (
  'bn-IN','en-IN','gu-IN','hi-IN','kn-IN','ml-IN','mr-IN','od-IN','pa-IN','ta-IN','te-IN'
));

-- 2. Composed messages: translated for the sender to share outside the app,
--    so they have a target language but nobody to deliver to.
alter table messages
  add column if not exists kind text not null default 'community'
  check (kind in ('community', 'composed'));

alter table messages alter column recipient drop not null;

alter table messages drop constraint if exists messages_recipient_matches_kind;
alter table messages add constraint messages_recipient_matches_kind
  check ((kind = 'community') = (recipient is not null));

create index if not exists messages_kind_idx on messages (sender, kind, created_at desc);
