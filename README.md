# Totus Telehealth 🇨🇦

**A PHIPA/PIPEDA/HIPAA-compliant Canadian telehealth video platform that beats Doxy.me.**

Browser-based, end-to-end encrypted video telehealth. No downloads for patients. One-click join. Beautiful, elderly-friendly UI.

## Features (Phase 1 — MVP)

- ✅ **Provider registration + login** with email/password and MFA
- ✅ **Unique URL** per provider (totus.ca/dr.smith)
- ✅ **Patient waiting room** — no account, no download, one click to join
- ✅ **Video call** with AES-256 end-to-end encryption
- ✅ **Screen sharing**
- ✅ **Virtual backgrounds**
- ✅ **Chat during call**
- ✅ **French/English toggle**
- ✅ **Dark mode**
- ✅ **Session history** (date, duration only)

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Frontend** | Next.js 16, TypeScript, Tailwind CSS v4, shadcn/ui |
| **Video** | LiveKit (self-hosted WebRTC SFU) with E2EE |
| **Database** | PostgreSQL 16 with pgcrypto encryption |
| **Auth** | NextAuth.js v5 (Credentials + MFA-ready) |
| **State** | Zustand |
| **Animation** | Framer Motion |
| **Hosting** | Canadian VPS (OVH Canada / Canadian Web Hosting) |

## Encryption Architecture

```
┌─────────────────────────────────────────────────────┐
│                  PATIENT BROWSER                     │
│  Layer 1: WebRTC DTLS-SRTP (AES-128)               │
│  Layer 2: LiveKit E2EE (AES-256-GCM)               │
│  Layer 3: TLS 1.3 (HTTPS)                           │
└──────────────────────┬──────────────────────────────┘
                       │ Encrypted media (server cannot decrypt)
                       ▼
┌─────────────────────────────────────────────────────┐
│              LIVEKIT SFU (CANADA)                    │
│  - Routes encrypted packets only                     │
│  - No plaintext PHI ever touches server              │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│                  PROVIDER BROWSER                    │
│  (same encryption layers as patient)                 │
└─────────────────────────────────────────────────────┘
```

## Quick Start (Development)

### Prerequisites

- Node.js 20+
- Docker & Docker Compose
- PostgreSQL 16 (optional — Docker handles it)

### Setup

```bash
# Clone the repo
git clone https://github.com/totuslifeservices-ux/totus-life-services.git
cd totus-telehealth

# Copy environment
cp .env.example .env
# Edit .env with your values

# Install dependencies
npm install

# Set up database
npx prisma generate
npx prisma migrate dev

# Start LiveKit + PostgreSQL + Redis
docker compose up -d postgres livekit redis

# Run the dev server
npm run dev
```

### Start Developing

```bash
npm run dev
# Open http://localhost:3000
```

## Production Deployment

```bash
# Build and run
docker compose up -d --build
```

## Project Structure

```
totus-telehealth/
├── prisma/                  # Database schema & migrations
├── scripts/                 # Utility scripts
├── src/
│   ├── app/
│   │   ├── (auth)/          # Login, register pages
│   │   ├── (dashboard)/     # Dashboard, room, settings
│   │   ├── (public)/        # Join, waiting room pages
│   │   ├── api/             # API routes
│   │   ├── layout.tsx       # Root layout
│   │   └── page.tsx         # Landing page
│   ├── components/
│   │   ├── features/        # App-specific components
│   │   └── ui/              # shadcn/ui components
│   ├── hooks/               # Custom React hooks
│   ├── i18n/                # Translations
│   ├── lib/                 # Utilities (db, auth, livekit)
│   └── store/               # Zustand stores
├── docker-compose.yml
├── Dockerfile
├── livekit.yaml
└── package.json
```

## Compliance

- **PHIPA** (Ontario) — primary design target
- **PIPEDA** (Federal) — data residency and consent
- **PIPA** (Alberta/BC) — private sector requirements
- **HIPAA** (US) — designed for eventual compliance
- All data hosted on Canadian servers
- End-to-end encryption for all video/audio
- Immutable audit logs for all PHI access
- Role-based access control
- 15-minute auto-lock on inactivity

## License

Private — Totus Life Services Inc.
