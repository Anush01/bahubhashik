import { Router } from "express";
import { supabase } from "../lib/supabase.js";
import { isLanguage, LANGUAGES } from "../lib/env.js";

export const users = Router();

users.get("/languages", (_req, res) => {
  res.json({ languages: LANGUAGES });
});

/**
 * Create-or-login. v0 identity is a bare username: typing an existing name
 * signs you in as that person. Deliberate, for an app with three users.
 */
users.post("/", async (req, res) => {
  const username = String(req.body?.username ?? "").trim();
  const language = req.body?.language;

  if (!username) return res.status(400).json({ error: "username is required" });
  if (username.length > 40) return res.status(400).json({ error: "username must be 40 characters or fewer" });
  if (!isLanguage(language)) {
    return res.status(400).json({ error: `language must be one of: ${LANGUAGES.join(", ")}` });
  }

  const { data: existing } = await supabase
    .from("users")
    .select("username, language")
    .eq("username", username)
    .maybeSingle();

  if (existing) {
    // Returning user may have changed their mind about their language.
    if (existing.language !== language) {
      const { error } = await supabase.from("users").update({ language }).eq("username", username);
      if (error) return res.status(500).json({ error: error.message });
    }
    return res.json({ username, language, created: false });
  }

  const { error } = await supabase.from("users").insert({ username, language });
  if (error) return res.status(500).json({ error: error.message });
  res.status(201).json({ username, language, created: true });
});

users.get("/", async (_req, res) => {
  const { data, error } = await supabase
    .from("users")
    .select("username, language")
    .order("username");
  if (error) return res.status(500).json({ error: error.message });
  res.json({ users: data });
});
