import "dotenv/config";

function required(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`Missing required env var ${name}. Copy .env.example to .env and fill it in.`);
  return value;
}

/**
 * Lazy on purpose. Tools that only touch Sarvam (scripts/try-pipeline.ts)
 * shouldn't fail because Supabase isn't configured yet, and vice versa —
 * each var is checked when something actually reads it.
 */
export const env = {
  get supabaseUrl() {
    return required("SUPABASE_URL");
  },
  /**
   * Supabase's new `sb_secret_...` key. It replaces the legacy `service_role`
   * JWT (deprecated end of 2026), is a drop-in for createClient(), and bypasses
   * RLS the same way — so it stays server-side, always.
   */
  get supabaseSecretKey() {
    const value = process.env.SUPABASE_SECRET_KEY ?? process.env.SUPABASE_SERVICE_ROLE_KEY;
    if (!value) {
      throw new Error(
        "Missing SUPABASE_SECRET_KEY. Supabase Dashboard > Project Settings > API Keys > " +
          "Secret keys (starts with sb_secret_). Copy .env.example to .env and fill it in.",
      );
    }
    return value;
  },
  get bucket() {
    return process.env.SUPABASE_BUCKET ?? "audio";
  },
  get sarvamApiKey() {
    return required("SARVAM_API_KEY");
  },
  get port() {
    return Number(process.env.PORT ?? 8787);
  },
};

/**
 * Every language bulbul:v3 can speak. Translation and transcription cover all
 * 22 scheduled languages, but output here is always audio, so the voice model
 * sets the limit.
 */
export const LANGUAGES = [
  "bn-IN", "en-IN", "gu-IN", "hi-IN", "kn-IN", "ml-IN",
  "mr-IN", "od-IN", "pa-IN", "ta-IN", "te-IN",
] as const;
export type Language = (typeof LANGUAGES)[number];

export function isLanguage(value: unknown): value is Language {
  return typeof value === "string" && (LANGUAGES as readonly string[]).includes(value);
}

/** Product cap: 5 minutes per message. */
export const MAX_MESSAGE_SECONDS = 5 * 60;
