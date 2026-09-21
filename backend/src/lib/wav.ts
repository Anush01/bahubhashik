/**
 * Sarvam TTS returns one audio blob per chunk. We stitch them into a single
 * playable file by extracting each blob's PCM payload and writing one header.
 */

export interface Pcm {
  data: Buffer;
  sampleRate: number;
  channels: number;
  bitsPerSample: number;
}

/** Pull the PCM payload out of a RIFF/WAVE buffer. Chunk layout varies, so walk it. */
export function parseWav(buffer: Buffer): Pcm {
  if (buffer.length < 12 || buffer.toString("ascii", 0, 4) !== "RIFF" || buffer.toString("ascii", 8, 12) !== "WAVE") {
    throw new Error("Not a RIFF/WAVE buffer");
  }

  let offset = 12;
  let sampleRate = 24000;
  let channels = 1;
  let bitsPerSample = 16;
  let data: Buffer | undefined;

  while (offset + 8 <= buffer.length) {
    const id = buffer.toString("ascii", offset, offset + 4);
    const size = buffer.readUInt32LE(offset + 4);
    const body = offset + 8;

    if (id === "fmt ") {
      channels = buffer.readUInt16LE(body + 2);
      sampleRate = buffer.readUInt32LE(body + 4);
      bitsPerSample = buffer.readUInt16LE(body + 14);
    } else if (id === "data") {
      // Some encoders write a placeholder size; clamp to what's actually there.
      data = buffer.subarray(body, Math.min(body + size, buffer.length));
    }

    offset = body + size + (size % 2); // chunks are word-aligned
  }

  if (!data) throw new Error("WAVE buffer has no data chunk");
  return { data, sampleRate, channels, bitsPerSample };
}

export function encodeWav(pcm: Pcm): Buffer {
  const { data, sampleRate, channels, bitsPerSample } = pcm;
  const byteRate = (sampleRate * channels * bitsPerSample) / 8;
  const blockAlign = (channels * bitsPerSample) / 8;

  const header = Buffer.alloc(44);
  header.write("RIFF", 0, "ascii");
  header.writeUInt32LE(36 + data.length, 4);
  header.write("WAVE", 8, "ascii");
  header.write("fmt ", 12, "ascii");
  header.writeUInt32LE(16, 16);
  header.writeUInt16LE(1, 20); // PCM
  header.writeUInt16LE(channels, 22);
  header.writeUInt32LE(sampleRate, 24);
  header.writeUInt32LE(byteRate, 28);
  header.writeUInt16LE(blockAlign, 32);
  header.writeUInt16LE(bitsPerSample, 34);
  header.write("data", 36, "ascii");
  header.writeUInt32LE(data.length, 40);

  return Buffer.concat([header, data]);
}

/**
 * Concatenate TTS chunks. Accepts either WAV blobs or headerless PCM
 * (depending on the codec Sarvam hands back) and always returns one WAV.
 */
export function concatAudio(buffers: Buffer[], fallbackSampleRate = 24000): Buffer {
  if (buffers.length === 0) throw new Error("Nothing to concatenate");

  const parts: Buffer[] = [];
  let sampleRate = fallbackSampleRate;
  let channels = 1;
  let bitsPerSample = 16;

  for (const buffer of buffers) {
    const isRiff = buffer.length >= 12 && buffer.toString("ascii", 0, 4) === "RIFF";
    if (isRiff) {
      const pcm = parseWav(buffer);
      sampleRate = pcm.sampleRate;
      channels = pcm.channels;
      bitsPerSample = pcm.bitsPerSample;
      parts.push(pcm.data);
    } else {
      parts.push(buffer);
    }
  }

  return encodeWav({ data: Buffer.concat(parts), sampleRate, channels, bitsPerSample });
}
