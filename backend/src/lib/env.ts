import "dotenv/config";

function required(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`Missing required env var ${name}. Copy .env.example to .env and fill it in.`);
  return value;
}

export const env = {
  supabaseUrl: required("SUPABASE_URL"),
  supabaseServiceRoleKey: required("SUPABASE_SERVICE_ROLE_KEY"),
  bucket: process.env.SUPABASE_BUCKET ?? "audio",
  sarvamApiKey: required("SARVAM_API_KEY"),
  port: Number(process.env.PORT ?? 8787),
};

export const LANGUAGES = ["en-IN", "hi-IN", "mr-IN", "gu-IN", "kn-IN"] as const;
export type Language = (typeof LANGUAGES)[number];

export function isLanguage(value: unknown): value is Language {
  return typeof value === "string" && (LANGUAGES as readonly string[]).includes(value);
}

/** Product cap: 5 minutes per message. */
export const MAX_MESSAGE_SECONDS = 5 * 60;
