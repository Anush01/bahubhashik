import express from "express";
import cors from "cors";
import { env } from "./lib/env.js";
import { ensureBucket } from "./lib/supabase.js";
import { users } from "./routes/users.js";
import { messages } from "./routes/messages.js";

const app = express();

app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

app.get("/health", (_req, res) => res.json({ ok: true }));
app.use("/users", users);
app.use("/messages", messages);

app.use((error: Error, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  console.error("[error]", error);
  res.status(500).json({ error: error.message });
});

await ensureBucket();

// 0.0.0.0 so a phone on the same Wi-Fi can reach it.
app.listen(env.port, "0.0.0.0", () => {
  console.log(`BahuBhashik backend listening on http://0.0.0.0:${env.port}`);
});
