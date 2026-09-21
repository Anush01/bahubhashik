import { Router } from "express";
import { supabase } from "../lib/supabase.js";
import { isLanguage, LANGUAGES } from "../lib/env.js";
import { isVoice, VOICES } from "../services/sarvam.js";

export const users = Router();

users.get("/languages", (_req, res) => {
  res.json({ languages: LANGUAGES, voices: Object.keys(VOICES) });
});

/**
 * Create-or-login. v0 identity is a bare username: typing an existing name
 * signs you in as that person. Deliberate, for an app with three users.
 */
users.post("/", async (req, res) => {
  const username = String(req.body?.username ?? "").trim();
  const language = req.body?.language;
  const voice = req.body?.voice ?? "female";

  if (!username) return res.status(400).json({ error: "username is required" });
  if (username.length > 40) return res.status(400).json({ error: "username must be 40 characters or fewer" });
  if (!isLanguage(language)) {
    return res.status(400).json({ error: `language must be one of: ${LANGUAGES.join(", ")}` });
  }
  if (!isVoice(voice)) {
    return res.status(400).json({ error: `voice must be one of: ${Object.keys(VOICES).join(", ")}` });
  }

  const { data: existing } = await supabase
    .from("users")
    .select("username, language, voice")
    .eq("username", username)
    .maybeSingle();

  if (existing) {
    // Returning user may have changed their mind about either setting.
    if (existing.language !== language || existing.voice !== voice) {
      const { error } = await supabase.from("users").update({ language, voice }).eq("username", username);
      if (error) return res.status(500).json({ error: error.message });
    }
    return res.json({ username, language, voice, created: false });
  }

  const { error } = await supabase.from("users").insert({ username, language, voice });
  if (error) return res.status(500).json({ error: error.message });
  res.status(201).json({ username, language, voice, created: true });
});

users.get("/", async (_req, res) => {
  const { data, error } = await supabase
    .from("users")
    .select("username, language, voice")
    .order("username");
  if (error) return res.status(500).json({ error: error.message });
  res.json({ users: data });
});
