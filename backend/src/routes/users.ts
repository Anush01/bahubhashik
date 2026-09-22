import { Router } from "express";
import { supabase } from "../lib/supabase.js";
import { isLanguage, LANGUAGES } from "../lib/env.js";
import { isVoice, VOICES } from "../services/sarvam.js";

export const users = Router();

/**
 * Columns safe to send to a client. The PIN is never among them — this list
 * feeds the recipient picker, which every user can read.
 */
const PUBLIC_COLUMNS = "username, language, voice";

const isPin = (value: unknown): value is string =>
  typeof value === "string" && /^[0-9]{4}$/.test(value);

function cleanUsername(value: unknown): string {
  return String(value ?? "").trim();
}

users.get("/languages", (_req, res) => {
  res.json({ languages: LANGUAGES, voices: Object.keys(VOICES) });
});

/**
 * What the app needs before it can decide which sign-in screen to show:
 * does this name exist, and does it have a PIN yet?
 *
 * This does confirm whether a username exists, which for a three-person
 * family app is not a secret worth protecting.
 */
users.get("/lookup", async (req, res) => {
  const username = cleanUsername(req.query.username);
  if (!username) return res.status(400).json({ error: "username is required" });

  const { data, error } = await supabase
    .from("users")
    .select("username, language, voice, pin")
    .eq("username", username)
    .maybeSingle();
  if (error) return res.status(500).json({ error: error.message });

  if (!data) return res.json({ exists: false, hasPin: false });
  res.json({
    exists: true,
    hasPin: data.pin !== null && data.pin !== "",
    username: data.username,
    language: data.language,
    voice: data.voice,
  });
});

/** Create a new person. Fails if the name is taken — signing in is a separate call. */
users.post("/", async (req, res) => {
  const username = cleanUsername(req.body?.username);
  const { language, voice = "female", pin } = req.body ?? {};

  if (!username) return res.status(400).json({ error: "username is required" });
  if (username.length > 40) return res.status(400).json({ error: "username must be 40 characters or fewer" });
  if (!isLanguage(language)) {
    return res.status(400).json({ error: `language must be one of: ${LANGUAGES.join(", ")}` });
  }
  if (!isVoice(voice)) {
    return res.status(400).json({ error: `voice must be one of: ${Object.keys(VOICES).join(", ")}` });
  }
  if (!isPin(pin)) return res.status(400).json({ error: "pin must be exactly 4 digits" });

  const { data: existing } = await supabase
    .from("users")
    .select("username")
    .eq("username", username)
    .maybeSingle();
  if (existing) return res.status(409).json({ error: `"${username}" is already taken` });

  const { data, error } = await supabase
    .from("users")
    .insert({ username, language, voice, pin })
    .select(PUBLIC_COLUMNS)
    .single();
  if (error) return res.status(500).json({ error: error.message });

  res.status(201).json(data);
});

/** Verify a PIN. The PIN is compared here and never sent to a client. */
users.post("/login", async (req, res) => {
  const username = cleanUsername(req.body?.username);
  const pin = req.body?.pin;

  if (!username) return res.status(400).json({ error: "username is required" });
  if (!isPin(pin)) return res.status(400).json({ error: "pin must be exactly 4 digits" });

  const { data, error } = await supabase
    .from("users")
    .select("username, language, voice, pin")
    .eq("username", username)
    .maybeSingle();
  if (error) return res.status(500).json({ error: error.message });
  if (!data) return res.status(404).json({ error: `no such person: "${username}"` });

  if (!data.pin) {
    return res.status(409).json({ error: "this person has no PIN yet — set one first" });
  }
  if (data.pin !== pin) {
    return res.status(401).json({ error: "that PIN doesn't match" });
  }

  res.json({ username: data.username, language: data.language, voice: data.voice });
});

/**
 * Set a first PIN for someone who signed up before PINs existed. Refuses to
 * overwrite an existing one, so nobody can take over another person's account
 * by claiming their name.
 */
users.post("/pin", async (req, res) => {
  const username = cleanUsername(req.body?.username);
  const pin = req.body?.pin;

  if (!username) return res.status(400).json({ error: "username is required" });
  if (!isPin(pin)) return res.status(400).json({ error: "pin must be exactly 4 digits" });

  const { data, error } = await supabase
    .from("users")
    .select("username, pin")
    .eq("username", username)
    .maybeSingle();
  if (error) return res.status(500).json({ error: error.message });
  if (!data) return res.status(404).json({ error: `no such person: "${username}"` });
  if (data.pin) return res.status(409).json({ error: "this person already has a PIN" });

  const { data: updated, error: updateError } = await supabase
    .from("users")
    .update({ pin })
    .eq("username", username)
    .select(PUBLIC_COLUMNS)
    .single();
  if (updateError) return res.status(500).json({ error: updateError.message });

  res.json(updated);
});

users.get("/", async (_req, res) => {
  const { data, error } = await supabase
    .from("users")
    .select(PUBLIC_COLUMNS)
    .order("username");
  if (error) return res.status(500).json({ error: error.message });
  res.json({ users: data });
});
