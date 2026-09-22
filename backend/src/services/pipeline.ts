import { supabase, uploadAudio } from "../lib/supabase.js";
import type { Language } from "../lib/env.js";
import { speakerFor, synthesize, transcribe, translate, type Voice } from "./sarvam.js";

export type MessageStatus =
  | "uploaded"
  | "transcribing"
  | "translating"
  | "synthesizing"
  | "ready"
  | "failed";

interface PipelineInput {
  messageId: string;
  senderVoice: Voice;
  audio: Buffer;
  filename: string;
  sourceLang: Language;
  targetLang: Language;
  durationSeconds: number | null;
}

async function setStatus(messageId: string, status: MessageStatus, fields: Record<string, unknown> = {}) {
  const { error } = await supabase
    .from("messages")
    .update({ status, updated_at: new Date().toISOString(), ...fields })
    .eq("id", messageId);
  if (error) console.error(`[pipeline] could not update ${messageId}:`, error.message);
}

/**
 * Speech -> text -> translated text -> speech.
 *
 * Runs in-process, detached from the request that triggered it: a 5-minute
 * message takes far longer than an HTTP request should. The client polls
 * GET /messages/:id and watches `status`. If the server restarts mid-run the
 * message is stranded in a non-terminal status — acceptable for v0, and the
 * reason every stage records its partial output as it goes.
 */
export async function runPipeline(input: PipelineInput): Promise<void> {
  const { messageId, senderVoice, audio, filename, sourceLang, targetLang, durationSeconds } = input;
  const started = Date.now();

  try {
    await setStatus(messageId, "transcribing");
    const sourceText = await transcribe(audio, filename, sourceLang, durationSeconds);
    if (!sourceText) throw new Error("Transcription came back empty — was the recording silent?");
    await setStatus(messageId, "translating", { source_text: sourceText });

    const translatedText = await translate(sourceText, sourceLang, targetLang);
    if (!translatedText) throw new Error("Translation came back empty");
    await setStatus(messageId, "synthesizing", { translated_text: translatedText });

    const speech = await synthesize(translatedText, targetLang, speakerFor(senderVoice));
    const path = `translated/${messageId}.mp3`;
    await uploadAudio(path, speech, "audio/mpeg");

    await setStatus(messageId, "ready", { translated_audio_path: path, error: null });
    console.log(`[pipeline] ${messageId} ready in ${((Date.now() - started) / 1000).toFixed(1)}s`);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error(`[pipeline] ${messageId} failed:`, message);
    await setStatus(messageId, "failed", { error: message });
  }
}
