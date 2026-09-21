import { createClient } from "@supabase/supabase-js";
import { env } from "./env.js";

export const supabase = createClient(env.supabaseUrl, env.supabaseSecretKey, {
  auth: { persistSession: false },
});

export async function ensureBucket(): Promise<void> {
  const { data, error } = await supabase.storage.listBuckets();
  if (error) throw new Error(`Could not list storage buckets: ${error.message}`);
  if (data.some((b) => b.name === env.bucket)) return;

  const { error: createError } = await supabase.storage.createBucket(env.bucket, { public: false });
  if (createError) throw new Error(`Could not create bucket "${env.bucket}": ${createError.message}`);
  console.log(`[storage] created bucket "${env.bucket}"`);
}

export async function uploadAudio(path: string, body: Buffer, contentType: string): Promise<string> {
  const { error } = await supabase.storage
    .from(env.bucket)
    .upload(path, body, { contentType, upsert: true });
  if (error) throw new Error(`Upload of ${path} failed: ${error.message}`);
  return path;
}

/** Storage is private, so playback needs a short-lived signed URL. */
export async function signedUrl(path: string, expiresInSeconds = 60 * 60): Promise<string> {
  const { data, error } = await supabase.storage
    .from(env.bucket)
    .createSignedUrl(path, expiresInSeconds);
  if (error) throw new Error(`Could not sign ${path}: ${error.message}`);
  return data.signedUrl;
}
