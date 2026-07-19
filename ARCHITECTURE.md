# Totus Telehealth

Canadian telehealth platform. 🇨🇦

PHIPA/PIPEDA/HIPAA-compliant, AES-256 E2EE, built to beat Doxy.me.

## Getting Started

1. `cp .env.example .env` and fill in secrets
2. `npm install`
3. `npx prisma migrate dev`
4. `docker compose up -d postgres livekit redis`
5. `npm run dev` → http://localhost:3000
