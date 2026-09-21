import { test } from "node:test";
import assert from "node:assert/strict";
import { chunkText } from "../src/lib/chunk.js";
import { concatAudio, encodeWav, parseWav } from "../src/lib/wav.js";

const MARATHI = "मी तुला काल फोन केला होता। तू घरी नव्हतीस। आज संध्याकाळी येशील का? मला तुझ्याशी बोलायचं आहे। ";

test("chunkText splits a five-minute-sized transcript within the cap", () => {
  const long = MARATHI.repeat(60); // ~5500 chars
  const chunks = chunkText(long, 1800);

  assert.ok(chunks.length > 1, "should split");
  assert.ok(chunks.every((c) => c.length <= 1800), "every chunk within cap");
  assert.equal(
    chunks.join(" ").split(/\s+/).filter(Boolean).length,
    long.split(/\s+/).filter(Boolean).length,
    "no words lost",
  );
});

test("chunkText passes short text through untouched", () => {
  assert.deepEqual(chunkText("नमस्कार", 1800), ["नमस्कार"]);
  assert.deepEqual(chunkText("   ", 1800), []);
});

test("chunkText handles speech with no sentence punctuation", () => {
  const chunks = chunkText("word ".repeat(1000), 500);
  assert.ok(chunks.every((c) => c.length <= 500));
});

test("chunkText breaks up a single oversized token instead of dropping it", () => {
  const token = "x".repeat(1200);
  const chunks = chunkText(token, 500);

  assert.ok(chunks.every((c) => c.length <= 500));
  assert.equal(chunks.join(""), token, "no characters lost");
});

const tone = (samples: number) => {
  const buffer = Buffer.alloc(samples * 2);
  for (let i = 0; i < samples; i++) buffer.writeInt16LE(Math.round(Math.sin(i / 10) * 8000), i * 2);
  return buffer;
};

test("wav roundtrips and concatenates TTS chunks", () => {
  const a = encodeWav({ data: tone(2400), sampleRate: 24000, channels: 1, bitsPerSample: 16 });
  const b = encodeWav({ data: tone(1200), sampleRate: 24000, channels: 1, bitsPerSample: 16 });

  assert.equal(parseWav(a).data.length, 4800);

  const joined = concatAudio([a, b]);
  const parsed = parseWav(joined);

  assert.equal(parsed.data.length, 4800 + 2400, "payloads joined");
  assert.equal(parsed.sampleRate, 24000, "sample rate preserved");
  assert.equal(joined.readUInt32LE(4), joined.length - 8, "RIFF size correct");
});

test("concatAudio accepts headerless PCM alongside wav", () => {
  const a = encodeWav({ data: tone(2400), sampleRate: 24000, channels: 1, bitsPerSample: 16 });
  assert.equal(parseWav(concatAudio([a, tone(600)])).data.length, 4800 + 1200);
});
