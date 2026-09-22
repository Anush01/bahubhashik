import lamejs from "@breezystack/lamejs";
import type { Pcm } from "./wav.js";

/** LAME consumes exactly this many samples per frame. */
const FRAME_SIZE = 1152;

/** Speech, mono. 64kbps is transparent enough and keeps files small on mobile data. */
const BITRATE_KBPS = 64;

/**
 * WhatsApp does not play WAV — its supported list is AAC, AMR, MP3, M4A and
 * OGG/Opus — and Android's own WAV support varies by device. Since the whole
 * point of saving a translation is sending it to someone without the app, the
 * file has to be one they can actually open.
 *
 * MP3 rather than AAC or Opus because it's the most universally playable, and
 * because a pure-JS encoder avoids shipping an ffmpeg binary to Render.
 *
 * Encoding happens once, on already-stitched audio: multi-chunk TTS output can
 * only be joined safely as PCM, so the join has to come first.
 */
export function encodeMp3(pcm: Pcm): Buffer {
  if (pcm.bitsPerSample !== 16) {
    throw new Error(`Expected 16-bit PCM to encode, got ${pcm.bitsPerSample}-bit`);
  }

  const encoder = new lamejs.Mp3Encoder(pcm.channels, pcm.sampleRate, BITRATE_KBPS);
  const samples = new Int16Array(
    pcm.data.buffer,
    pcm.data.byteOffset,
    Math.floor(pcm.data.byteLength / 2),
  );

  const parts: Buffer[] = [];
  for (let offset = 0; offset < samples.length; offset += FRAME_SIZE) {
    const frame = samples.subarray(offset, offset + FRAME_SIZE);
    const encoded = encoder.encodeBuffer(frame);
    if (encoded.length > 0) parts.push(Buffer.from(encoded));
  }

  const trailing = encoder.flush();
  if (trailing.length > 0) parts.push(Buffer.from(trailing));

  return Buffer.concat(parts);
}
