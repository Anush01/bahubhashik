-- Migration for databases created before the voice picker existed.
-- New databases get this from schema.sql directly.

alter table users
  add column if not exists voice text not null default 'female'
  check (voice in ('female', 'male'));
