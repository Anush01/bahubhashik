/**
 * Prove the Sarvam pipeline end to end on one audio file — no database,
 * no storage, no app. Run this before trusting anything downstream.
 *
 *   npx tsx scripts/try-pipeline.ts sample.wav mr-IN kn-IN
 *
 * Writes the translated audio next to the input as <name>.<target>.wav
 * and prints both transcripts plus a cost estimate.
 */
import { readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { isLanguage, LANGUAGES, type Language } from "../src/lib/env.js";
import { speakerFor, synthesize, transcribe, translate } from "../src/services/sarvam.js";

const [inputPath, from, to, durationArg] = process.argv.slice(2);

if (!inputPath || !isLanguage(from) || !isLanguage(to)) {
  console.error("usage: npx tsx scripts/try-pipeline.ts <audio-file> <source-lang> <target-lang> [duration-seconds]");
  console.error(`langs: ${LANGUAGES.join(", ")}`);
  process.exit(1);
}

const audio = await readFile(inputPath);
const duration = durationArg ? Number(durationArg) : null;
if (duration === null) {
  console.log("No duration given — using the batch STT path. Pass one (e.g. 20) to use the faster sync path.\n");
}

console.log(`[1/3] transcribing ${path.basename(inputPath)} (${from}, ${(audio.length / 1024).toFixed(0)}KB)…`);
const sourceText = await transcribe(audio, path.basename(inputPath), from as Language, duration);
console.log(`\n  ${sourceText}\n`);

console.log(`[2/3] translating ${from} -> ${to}…`);
const translatedText = await translate(sourceText, from as Language, to as Language);
console.log(`\n  ${translatedText}\n`);

console.log(`[3/3] synthesizing (${to})…`);
const speech = await synthesize(translatedText, to as Language, speakerFor("demo"));

const outputPath = inputPath.replace(/\.[^.]+$/, "") + `.${to}.mp3`;
await writeFile(outputPath, speech);

// Rates from https://docs.sarvam.ai/api/getting-started/pricing
const sttCost = duration !== null ? (duration / 3600) * 30 : 0;
const cost = sttCost + (sourceText.length / 10_000) * 20 + (translatedText.length / 10_000) * 30;

console.log(`\nWrote ${outputPath} (${(speech.length / 1024).toFixed(0)}KB)`);
console.log(
  `Approx cost: ₹${cost.toFixed(2)}` +
    (duration === null ? " (excludes transcription — pass a duration to include it)" : ""),
);
