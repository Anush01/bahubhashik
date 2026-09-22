-- Migration for databases created before PINs existed.
-- Nullable on purpose: people who signed up earlier get asked to set one
-- on their next sign-in rather than being locked out.

alter table users
  add column if not exists pin text
  check (pin is null or pin ~ '^[0-9]{4}$');
