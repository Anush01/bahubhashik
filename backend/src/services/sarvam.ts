import { env, type Language } from "../lib/env.js";
import { chunkText } from "../lib/chunk.js";
import { concatAudio } from "../lib/wav.js";

const BASE = "https://api.sarvam.ai";

// Per Sarvam's documented caps, with headroom for the joining whitespace.
const TRANSLATE_MAX_CHARS = 1800; // sarvam-translate:v1 allows 2000
const TTS_MAX_CHARS = 2300;       // bulbul:v3 allows 2500

// The synchronous STT endpoint rejects audio over 30s; longer goes to the batch API.
const SYNC_STT_MAX_SECONDS = 28;

const SPEAKERS = ["anushka", "vidya", "manisha", "arya", "abhilash", "karun"] as const;

function headers(extra: Record<string, string> = {}): Record<string, string> {
  return { "api-subscription-key": env.sarvamApiKey, ...extra };
}

async function readError(response: Response, label: string): Promise<Error> {
  const body = await response.text().catch(() => "");
  return new Error(`Sarvam ${label} failed (${response.status}): ${body.slice(0, 500)}`);
}

/** Same sender always gets the same synthesized voice, so they stay recognizable. */
export function speakerFor(username: string): string {
  let hash = 0;
  for (const char of username) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  return SPEAKERS[hash % SPEAKERS.length]!;
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
  const form = new FormData();
  form.append("file", new Blob([new Uint8Array(audio)]), filename);
  form.append("model", "saaras:v3");
  form.append("language_code", language);

  const response = await fetch(`${BASE}/speech-to-text`, {
    method: "POST",
    headers: headers(),
    body: form,
  });
  if (!response.ok) throw await readError(response, "speech-to-text");

  const json = (await response.json()) as { transcript?: string };
  return json.transcript?.trim() ?? "";
}

/**
 * Batch STT is a five-step dance: create job, upload, start, poll, download.
 * Used for anything over 30 seconds, which is most real messages.
 */
async function transcribeBatch(audio: Buffer, filename: string, language: Language): Promise<string> {
  const createResponse = await fetch(`${BASE}/speech-to-text/job/v1`, {
    method: "POST",
    headers: headers({ "Content-Type": "application/json" }),
    body: JSON.stringify({ model: "saaras:v3", mode: "transcribe", language_code: language }),
  });
  if (!createResponse.ok) throw await readError(createResponse, "batch job create");

  const { job_id: jobId } = (await createResponse.json()) as { job_id: string };
  if (!jobId) throw new Error("Sarvam batch job create returned no job_id");

  const form = new FormData();
  form.append("job_id", jobId);
  form.append("file", new Blob([new Uint8Array(audio)]), filename);
  const uploadResponse = await fetch(`${BASE}/speech-to-text/job/v1/upload-files`, {
    method: "POST",
    headers: headers(),
    body: form,
  });
  if (!uploadResponse.ok) throw await readError(uploadResponse, "batch upload");

  const startResponse = await fetch(`${BASE}/speech-to-text/job/v1/start`, {
    method: "POST",
    headers: headers({ "Content-Type": "application/json" }),
    body: JSON.stringify({ job_id: jobId }),
  });
  if (!startResponse.ok) throw await readError(startResponse, "batch start");

  const outputFile = await pollBatchJob(jobId);

  const downloadResponse = await fetch(`${BASE}/speech-to-text/job/v1/download-files`, {
    method: "POST",
    headers: headers({ "Content-Type": "application/json" }),
    body: JSON.stringify({ job_id: jobId, files: [outputFile] }),
  });
  if (!downloadResponse.ok) throw await readError(downloadResponse, "batch download");

  const payload = await downloadResponse.json();
  return extractTranscript(payload);
}

/** Sarvam asks for a 5s minimum poll interval. 10 minutes covers a 5-minute message comfortably. */
async function pollBatchJob(jobId: string, intervalMs = 5000, timeoutMs = 600_000): Promise<string> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    await new Promise((resolve) => setTimeout(resolve, intervalMs));

    const response = await fetch(`${BASE}/speech-to-text/job/v1/${jobId}/status`, { headers: headers() });
    if (!response.ok) throw await readError(response, "batch status");

    const status = (await response.json()) as {
      job_state?: string;
      job_details?: Array<{ file_name?: string; output_file_name?: string; error?: string }>;
    };

    if (status.job_state === "Failed") {
      const reason = status.job_details?.find((d) => d.error)?.error ?? "no reason given";
      throw new Error(`Sarvam batch job ${jobId} failed: ${reason}`);
    }
    if (status.job_state === "Completed") {
      return status.job_details?.[0]?.output_file_name ?? "0.json";
    }
  }

  throw new Error(`Sarvam batch job ${jobId} did not finish within ${timeoutMs / 1000}s`);
}

/** Download shape varies (bare object, array, or keyed by filename); accept all of them. */
function extractTranscript(payload: unknown): string {
  const candidate = Array.isArray(payload)
    ? payload[0]
    : typeof payload === "object" && payload !== null && !("transcript" in payload)
      ? Object.values(payload as Record<string, unknown>)[0]
      : payload;

  const transcript = (candidate as { transcript?: string } | undefined)?.transcript;
  if (typeof transcript !== "string") {
    throw new Error(`Could not find a transcript in Sarvam's response: ${JSON.stringify(payload).slice(0, 300)}`);
  }
  return transcript.trim();
}

// ----------------------------------------------------------------- translate

export async function translate(text: string, from: Language, to: Language): Promise<string> {
  if (from === to) return text;

  const chunks = chunkText(text, TRANSLATE_MAX_CHARS);
  const out: string[] = [];

  for (const chunk of chunks) {
    const response = await fetch(`${BASE}/translate`, {
      method: "POST",
      headers: headers({ "Content-Type": "application/json" }),
      body: JSON.stringify({
        input: chunk,
        source_language_code: from,
        target_language_code: to,
        model: "sarvam-translate:v1",
        // These are personal voice notes between friends, not documents.
        mode: "modern-colloquial",
      }),
    });
    if (!response.ok) throw await readError(response, "translate");

    const json = (await response.json()) as { translated_text?: string };
    out.push(json.translated_text ?? "");
  }

  return out.join(" ").trim();
}

// ----------------------------------------------------------------------- tts

export async function synthesize(text: string, language: Language, speaker: string): Promise<Buffer> {
  const chunks = chunkText(text, TTS_MAX_CHARS);
  if (chunks.length === 0) throw new Error("Nothing to synthesize");

  const buffers: Buffer[] = [];

  for (const chunk of chunks) {
    const response = await fetch(`${BASE}/text-to-speech`, {
      method: "POST",
      headers: headers({ "Content-Type": "application/json" }),
      body: JSON.stringify({
        text: chunk,
        target_language_code: language,
        speaker,
        model: "bulbul:v3",
        output_audio_codec: "wav",
      }),
    });
    if (!response.ok) throw await readError(response, "text-to-speech");

    const json = (await response.json()) as { audios?: string[] };
    for (const base64 of json.audios ?? []) buffers.push(Buffer.from(base64, "base64"));
  }

  return concatAudio(buffers);
}
