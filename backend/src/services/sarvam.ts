import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { SarvamAIClient } from "sarvamai";
import { env, type Language } from "../lib/env.js";
import { chunkText } from "../lib/chunk.js";
import { concatAudio, parseWav } from "../lib/wav.js";
import { encodeMp3 } from "../lib/mp3.js";

/**
 * Sarvam's own SDK rather than hand-rolled fetch calls.
 *
 * The batch transcription flow in particular is not something to reimplement:
 * it creates a job, asks for presigned Azure URLs, uploads to those, starts
 * the job, polls, then fetches results through more presigned URLs. Three of
 * those steps have request shapes the public docs get wrong.
 */
const client = new SarvamAIClient({ apiSubscriptionKey: env.sarvamApiKey });

// Per Sarvam's documented caps, with headroom for the joining whitespace.
const TTS_MAX_CHARS = 2300; // bulbul:v3 allows 2500

/** The synchronous endpoint rejects anything longer; past this we batch. */
const SYNC_STT_MAX_SECONDS = 28;

/**
 * The two translation models differ in more than register:
 *   mayura:v1            1000 chars, colloquial modes, transliteration
 *   sarvam-translate:v1  2000 chars, formal only (rejects any other mode)
 *
 * Colloquial sounds like the better fit for voice notes, but measured on real
 * Marathi->Kannada speech mayura leaves borrowed English in LATIN script
 * ("tree park", "doctor"), which Kannada TTS cannot pronounce.
 * sarvam-translate returns fully native script, so it wins on the thing that
 * actually matters here: the output has to be speakable.
 */
export const TRANSLATE_MODELS = {
  "sarvam-translate:v1": { maxChars: 1800, mode: "formal" },
  "mayura:v1": { maxChars: 900, mode: "modern-colloquial" },
} as const;

export type TranslateModel = keyof typeof TRANSLATE_MODELS;
export const DEFAULT_TRANSLATE_MODEL: TranslateModel = "sarvam-translate:v1";

/**
 * bulbul:v3 speakers. v2's names (anushka, vidya, manisha...) are rejected by
 * v3. One per voice option; people pick theirs at signup, so the same sender
 * always sounds the same to the person receiving them.
 */
export const VOICES = { female: "ritu", male: "shubh" } as const;
export type Voice = keyof typeof VOICES;

export function isVoice(value: unknown): value is Voice {
  return value === "female" || value === "male";
}

export function speakerFor(voice: Voice): string {
  return VOICES[voice];
}

// ---------------------------------------------------------------- transcribe

export async function transcribe(
  audio: Buffer,
  filename: string,
  language: Language,
  durationSeconds: number | null,
): Promise<string> {
  const useSync = durationSeconds !== null && durationSeconds <= SYNC_STT_MAX_SECONDS;
  return useSync
    ? transcribeSync(audio, filename, language)
    : transcribeBatch(audio, filename, language);
}

async function transcribeSync(audio: Buffer, filename: string, language: Language): Promise<string> {
  const response = await client.speechToText.transcribe({
    file: new File([new Uint8Array(audio)], filename),
    model: "saaras:v3",
    language_code: language,
  });
  return response.transcript?.trim() ?? "";
}

/**
 * Anything over 30 seconds, which is most real messages. The SDK handles the
 * upload-link dance; we only have to give it a file on disk, so the recording
 * is written to a temp file and cleaned up afterwards.
 */
async function transcribeBatch(audio: Buffer, filename: string, language: Language): Promise<string> {
  const directory = await mkdtemp(path.join(tmpdir(), "bahubhashik-"));
  const inputPath = path.join(directory, sanitise(filename));
  const outputDirectory = path.join(directory, "out");

  try {
    await writeFile(inputPath, audio);

    const job = await client.speechToTextJob.createJob({
      model: "saaras:v3",
      mode: "transcribe",
      languageCode: language,
    });

    await job.uploadFiles([inputPath]);
    await job.start();
    await job.waitUntilComplete();

    const results = await job.getFileResults();
    if (results.failed.length > 0) {
      throw new Error(
        `Sarvam could not transcribe the recording: ${results.failed[0]?.error_message ?? "no reason given"}`,
      );
    }

    await job.downloadOutputs(outputDirectory);
    return await readTranscript(outputDirectory);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
}

/** Sarvam writes one JSON file per input; we only ever send one. */
async function readTranscript(directory: string): Promise<string> {
  const { readdir } = await import("node:fs/promises");
  const names = await readdir(directory);
  const jsonName = names.find((name) => name.endsWith(".json"));
  if (!jsonName) throw new Error(`Sarvam returned no transcript file (found: ${names.join(", ") || "nothing"})`);

  const parsed = JSON.parse(await readFile(path.join(directory, jsonName), "utf8")) as {
    transcript?: string;
  };
  if (typeof parsed.transcript !== "string") {
    throw new Error("Sarvam's transcript file had no transcript field");
  }
  return parsed.transcript.trim();
}

/** Keep the extension — Sarvam infers the audio format from it. */
function sanitise(filename: string): string {
  const cleaned = filename.replace(/[^a-zA-Z0-9._-]/g, "_");
  return cleaned || "recording.m4a";
}

// ----------------------------------------------------------------- translate

export async function translate(
  text: string,
  from: Language,
  to: Language,
  model: TranslateModel = DEFAULT_TRANSLATE_MODEL,
): Promise<string> {
  if (from === to) return text;

  const { maxChars, mode } = TRANSLATE_MODELS[model];
  const out: string[] = [];

  for (const chunk of chunkText(text, maxChars)) {
    const response = await client.text.translate({
      input: chunk,
      source_language_code: from,
      target_language_code: to,
      model,
      mode,
    });
    out.push(response.translated_text ?? "");
  }

  return out.join(" ").trim();
}

// ----------------------------------------------------------------------- tts

/**
 * Returns MP3. Sarvam can emit mp3 directly, but only per request — and a
 * five-minute message needs several requests, which can only be joined
 * safely as PCM. So chunks come back as WAV, get stitched, and the result is
 * encoded once.
 */
export async function synthesize(text: string, language: Language, speaker: string): Promise<Buffer> {
  const chunks = chunkText(text, TTS_MAX_CHARS);
  if (chunks.length === 0) throw new Error("Nothing to synthesize");

  const buffers: Buffer[] = [];

  for (const chunk of chunks) {
    const response = await client.textToSpeech.convert({
      text: chunk,
      language_code: language,
      // Typed as a union of every voice Sarvam ships; ours come from VOICES.
      speaker: speaker as Parameters<typeof client.textToSpeech.convert>[0]["speaker"],
      model: "bulbul:v3",
      output_audio_codec: "wav",
    });

    for (const base64 of response.audios ?? []) buffers.push(Buffer.from(base64, "base64"));
  }

  return encodeMp3(parseWav(concatAudio(buffers)));
}
