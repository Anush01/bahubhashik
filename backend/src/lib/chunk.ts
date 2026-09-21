/**
 * Sarvam caps input per request: translate at 2000 chars, TTS at 2500.
 * A 5-minute message is ~4500 chars, so both stages need splitting.
 * We split on sentence boundaries so chunks never cut mid-word, which
 * would produce audible seams in the synthesized audio.
 */

const SENTENCE_BOUNDARY = /(?<=[.!?।॥])\s+/u; // includes Devanagari danda/double danda

export function chunkText(text: string, maxChars: number): string[] {
  const trimmed = text.trim();
  if (!trimmed) return [];
  if (trimmed.length <= maxChars) return [trimmed];

  const pieces = trimmed.split(SENTENCE_BOUNDARY).flatMap((s) => splitOversized(s, maxChars));

  const chunks: string[] = [];
  let current = "";
  for (const piece of pieces) {
    if (!piece) continue;
    const candidate = current ? `${current} ${piece}` : piece;
    if (candidate.length > maxChars && current) {
      chunks.push(current);
      current = piece;
    } else {
      current = candidate;
    }
  }
  if (current) chunks.push(current);
  return chunks;
}

/** A single "sentence" longer than the cap (no punctuation) still has to fit; break on whitespace. */
function splitOversized(sentence: string, maxChars: number): string[] {
  if (sentence.length <= maxChars) return [sentence];

  const out: string[] = [];
  let current = "";

  const flush = () => {
    if (current) out.push(current);
    current = "";
  };

  for (const word of sentence.split(/\s+/)) {
    if (!word) continue;

    // A single token past the cap can't be split on whitespace; cut it up
    // rather than truncating, which would silently drop the speaker's words.
    if (word.length > maxChars) {
      flush();
      for (let i = 0; i < word.length; i += maxChars) out.push(word.slice(i, i + maxChars));
      continue;
    }

    const candidate = current ? `${current} ${word}` : word;
    if (candidate.length > maxChars) {
      flush();
      current = word;
    } else {
      current = candidate;
    }
  }

  flush();
  return out;
}
