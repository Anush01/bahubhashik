import { Router } from "express";
import multer from "multer";
import { supabase, signedUrl, uploadAudio } from "../lib/supabase.js";
import { isLanguage, MAX_MESSAGE_SECONDS, type Language } from "../lib/env.js";
import { runPipeline } from "../services/pipeline.js";

export const messages = Router();

// 5 minutes of compressed mobile audio is a few MB; 40MB is generous headroom.
const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 40 * 1024 * 1024 } });

interface MessageRow {
  id: string;
  sender: string;
  recipient: string;
  status: string;
  source_lang: string;
  target_lang: string;
  original_audio_path: string | null;
  translated_audio_path: string | null;
  source_text: string | null;
  translated_text: string | null;
  duration_seconds: number | null;
  error: string | null;
  created_at: string;
}

/** Storage is private, so hand the client short-lived signed URLs rather than paths. */
async function present(row: MessageRow) {
  const [originalUrl, translatedUrl] = await Promise.all([
    row.original_audio_path ? signedUrl(row.original_audio_path).catch(() => null) : null,
    row.translated_audio_path ? signedUrl(row.translated_audio_path).catch(() => null) : null,
  ]);

  return {
    id: row.id,
    sender: row.sender,
    recipient: row.recipient,
    status: row.status,
    sourceLang: row.source_lang,
    targetLang: row.target_lang,
    sourceText: row.source_text,
    translatedText: row.translated_text,
    originalAudioUrl: originalUrl,
    translatedAudioUrl: translatedUrl,
    durationSeconds: row.duration_seconds,
    error: row.error,
    createdAt: row.created_at,
  };
}

/**
 * Inbox:        GET /messages?user=sunita
 * Conversation: GET /messages?user=sunita&with=lakshmi   (both directions)
 */
messages.get("/", async (req, res) => {
  const user = String(req.query.user ?? "").trim();
  const other = String(req.query.with ?? "").trim();
  if (!user) return res.status(400).json({ error: "user query parameter is required" });

  let query = supabase.from("messages").select("*").order("created_at", { ascending: false });

  query = other
    ? query.or(
        `and(sender.eq.${user},recipient.eq.${other}),and(sender.eq.${other},recipient.eq.${user})`,
      )
    : query.eq("recipient", user);

  const { data, error } = await query;
  if (error) return res.status(500).json({ error: error.message });

  res.json({ messages: await Promise.all((data as MessageRow[]).map(present)) });
});

messages.get("/:id", async (req, res) => {
  const { data, error } = await supabase
    .from("messages")
    .select("*")
    .eq("id", req.params.id)
    .maybeSingle();

  if (error) return res.status(500).json({ error: error.message });
  if (!data) return res.status(404).json({ error: "message not found" });
  res.json(await present(data as MessageRow));
});

messages.post("/", upload.single("audio"), async (req, res) => {
  const sender = String(req.body?.sender ?? "").trim();
  const recipient = String(req.body?.recipient ?? "").trim();
  const audio = req.file;

  if (!sender || !recipient) return res.status(400).json({ error: "sender and recipient are required" });
  if (sender === recipient) return res.status(400).json({ error: "cannot send a message to yourself" });
  if (!audio) return res.status(400).json({ error: "an audio file is required (field name: audio)" });

  const rawDuration = req.body?.durationSeconds;
  const durationSeconds = rawDuration === undefined || rawDuration === "" ? null : Number(rawDuration);
  if (durationSeconds !== null && !Number.isFinite(durationSeconds)) {
    return res.status(400).json({ error: "durationSeconds must be a number" });
  }
  if (durationSeconds !== null && durationSeconds > MAX_MESSAGE_SECONDS) {
    return res.status(400).json({ error: `messages are limited to ${MAX_MESSAGE_SECONDS / 60} minutes` });
  }

  const { data: people, error: peopleError } = await supabase
    .from("users")
    .select("username, language")
    .in("username", [sender, recipient]);
  if (peopleError) return res.status(500).json({ error: peopleError.message });

  const senderRow = people?.find((p) => p.username === sender);
  const recipientRow = people?.find((p) => p.username === recipient);
  if (!senderRow) return res.status(404).json({ error: `unknown sender "${sender}"` });
  if (!recipientRow) return res.status(404).json({ error: `unknown recipient "${recipient}"` });

  const sourceLang = senderRow.language;
  const targetLang = recipientRow.language;
  if (!isLanguage(sourceLang) || !isLanguage(targetLang)) {
    return res.status(500).json({ error: "a participant has an unsupported language on file" });
  }

  const { data: created, error: insertError } = await supabase
    .from("messages")
    .insert({
      sender,
      recipient,
      status: "uploaded",
      source_lang: sourceLang,
      target_lang: targetLang,
      // Column is int; clients report fractional seconds.
      duration_seconds: durationSeconds === null ? null : Math.round(durationSeconds),
    })
    .select("*")
    .single();
  if (insertError) return res.status(500).json({ error: insertError.message });

  const row = created as MessageRow;
  const extension = (audio.originalname.split(".").pop() ?? "m4a").toLowerCase();
  const path = `original/${row.id}.${extension}`;

  try {
    await uploadAudio(path, audio.buffer, audio.mimetype || "audio/m4a");
    await supabase.from("messages").update({ original_audio_path: path }).eq("id", row.id);
  } catch (error) {
    const reason = error instanceof Error ? error.message : String(error);
    await supabase.from("messages").update({ status: "failed", error: reason }).eq("id", row.id);
    return res.status(500).json({ error: reason });
  }

  // Detached on purpose: transcription alone outlasts any reasonable request
  // timeout. The client polls GET /messages/:id.
  void runPipeline({
    messageId: row.id,
    sender,
    audio: audio.buffer,
    filename: audio.originalname || `${row.id}.${extension}`,
    sourceLang: sourceLang as Language,
    targetLang: targetLang as Language,
    durationSeconds,
  });

  res.status(202).json(await present({ ...row, original_audio_path: path }));
});

/** Re-run a failed message without making the sender record it again. */
messages.post("/:id/retry", async (req, res) => {
  const { data, error } = await supabase
    .from("messages")
    .select("*")
    .eq("id", req.params.id)
    .maybeSingle();
  if (error) return res.status(500).json({ error: error.message });
  if (!data) return res.status(404).json({ error: "message not found" });

  const row = data as MessageRow;
  if (!row.original_audio_path) return res.status(400).json({ error: "message has no original audio to retry" });

  const { data: download, error: downloadError } = await supabase.storage
    .from(process.env.SUPABASE_BUCKET ?? "audio")
    .download(row.original_audio_path);
  if (downloadError || !download) {
    return res.status(500).json({ error: downloadError?.message ?? "could not read the original audio" });
  }

  const audio = Buffer.from(await download.arrayBuffer());
  void runPipeline({
    messageId: row.id,
    sender: row.sender,
    audio,
    filename: row.original_audio_path.split("/").pop() ?? `${row.id}.m4a`,
    sourceLang: row.source_lang as Language,
    targetLang: row.target_lang as Language,
    durationSeconds: row.duration_seconds,
  });

  res.status(202).json({ id: row.id, status: "uploaded" });
});
