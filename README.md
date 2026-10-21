# mcode

Claude Code jaisa AI coding agent, Android app me. Koi bhi OpenAI-compatible provider ki API key chalti hai
(Groq, Gemini, OpenRouter, OpenAI, Ollama, custom URL). Commands Termux me chalte hain.

## APK banana (GitHub se, phone se bhi)
1. Is folder ko apne GitHub repo me push karo (branch `main`).
2. Repo > **Actions** > **Build APK** > run (push par apne aap chalta hai).
3. Run khatam hone par **Artifacts** se `mcode-debug-apk` download karo, unzip karke `app-debug.apk` install karo.

## Termux setup (ek baar)
1. Termux **F-Droid** se install karo (Play Store wala purana hai).
2. Termux me:
   ```
   mkdir -p ~/.termux
   echo "allow-external-apps = true" >> ~/.termux/termux.properties
   termux-reload-settings
   pkg install git python nodejs
   ```
3. Android Settings > Apps > mcode > Permissions > **Additional permissions** > "Run commands in Termux environment" allow karo.
4. Termux ki battery optimization band karo (Unrestricted), warna commands beech me ruk sakti hain.
5. 

## Features
- OpenAI-compatible API (base URL + key + model)
- Tools: run_command, read_file, write_file, list_dir, grep
- Project folder me `CLAUDE.md` / `AGENTS.md` / `MCODE.md` ho to agent use pehle padhta hai
- API key encrypted storage me

## Limits / TODO
- Streaming jawab abhi nahi (poora jawab ek saath aata hai)
- Chat history app band hone par save nahi hoti
- Bahut badi files (500KB+) intent limit ki wajah se fail ho sakti hain
