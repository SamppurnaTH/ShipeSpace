# <img src="assets/shipspace_logo.png" width="100" height="100" align="center" /> ShipSpace: Master Project Documentation

## 1. Executive Summary & Product Vision

**ShipSpace** is a platform designed specifically for technical builders—Indie Hackers, Side-Project Developers, and Early-Stage Founders—to share their progress through a social layer built on "Proof of Work."

### 1.1 The Problem
Existing platforms like Twitter, LinkedIn, and GitHub fail to provide a dedicated space where the atomic unit of content is a **Project Update**.
- **Twitter/X:** Noise and engagement bait drown out technical validation.
- **LinkedIn:** Focused on corporate signaling rather than raw building.
- **GitHub:** Great for code, but lacks a social discovery layer for products.

### 1.2 The Solution
ShipSpace organizes content around **Projects**. Users publish **Ships** (Project Updates) that are pinned to persistent project entities, creating a historical log of building in public.

### 1.3 Strategic Boundaries
- **Proof of Work > Engagement:** Every "Ship" must be attached to a Project.
- **Radical Transparency:** Metrics and progress are the focus.
- **Builder-First Moderation:** Constructive feedback is prioritized over generic praise.
- **Strict Scope:** No code hosting, no standalone tutorials, and no general discourse/politics.

---

## 2. Product Strategy & Metrics

### 2.1 North Star Metric: Weekly Active Shipping Builders (WASB)
The number of unique users per week who publish at least one meaningful Project Update.

### 2.2 Key Performance Indicators (KPIs)
- **Project Update Frequency (PUF):** Target ≥ 1.5 updates per builder/week.
- **Feedback Velocity (FV):** Median ≥ 3 meaningful comments per ship within 48 hours.
- **Builder Retention (4WBR):** Target ≥ 25% retention after 4 weeks.
- **Signal-to-Noise Ratio (SNR):** Target ≥ 80% project-linked content.

---

## 3. Product Features & User Experience

### 3.1 Authentication & Profile
- **Identity:** Mandatory GitHub OAuth 2.0 (Supabase Auth). No email/password for MVP to ensure user quality.
- **Profiles:** Displays avatar, bio, tech stack tags, and a **"Shipper Badge"** for active builders.
- **Progression (ShipScore):** A gamified system where users earn points for shipping and engaging.
    - **Ranks:** DOCKED (Lvl 1-5) up to FLEET_ADMIRAL (Lvl 26-30).

### 3.2 Projects & Ships
- **Project Entity:** Contains Name, One-Liner, Status (Idea, Building, Live, Archived), and URLs (Website/Repo).
- **Ship (Update):** An atomic post tied to a Project. Includes text (max 500 chars), code snippets, and up to 4 images.
- **Editing Grace Period:** Ships are editable for 10 minutes, then locked as immutable historical records.

### 3.3 Social Graph & Feeds
- **Following Feed:** Pure reverse-chronological feed of followed builders.
- **Trending Feed:** A discovery feed using an engagement-based ranking algorithm normalized by follower count and time decay.
- **Engagement:** Rocket (🚀) likes and threaded comments (max depth 3).

---

## 4. Technical Architecture

### 4.1 Technology Stack
- **Frontend:** Next.js 14 (App Router) on Vercel.
- **Backend/API:** Supabase Edge Functions (Deno/TypeScript) for writes; PostgREST for reads.
- **Database:** PostgreSQL 16 (Supabase Managed).
- **Storage:** Cloudflare R2 (Private Bucket) for media, stripped of EXIF data.
- **Caching:** Redis-backed caching for interest profiles and content scores.

### 4.2 System Design Decisions (ADR)
- **ADR-001:** Projects as atomic units ensures high-signal content.
- **ADR-006:** Split Read/Write paths (PostgREST for speed, Edge Functions for validation).
- **ADR-007:** Private Media Storage with Signed Delivery for security and cost control.
- **ADR-008:** No native "Re-ship" feature to maintain focus on original content.

---

## 5. Implementation Status & History

### 5.1 Project Setup (Phase 1)
The foundation was established with Spring Boot 3.2, MongoDB (later transitioned/integrated for specific personalization features), and GitHub OAuth.

### 5.2 Personalized Feed Algorithm (85% Complete)
A sophisticated hybrid feed system was implemented:
- **Scoring System:** Quality (Quality + Engagement), Relevance (Tech Tag Matching), and Performance (Cohort Comparison).
- **Cold-Start Handling:** Fallback to trending content for new users and visibility boosts for new content.
- **Behavioral Tracking:** Asynchronous tracking of likes, comments, views, and follows to update user interest profiles.
- **Current Blocker:** A structural issue in `PersonalizationService.java` requires a class scope fix.

### 5.3 Core Services Implemented
- **ProjectService:** CRUD operations and ownership validation.
- **FeedService:** Chronological and trending feed generation.
- **EngagementService:** Toggling likes and comment management.
- **ShipScoreService:** Progression and rank calculation.

---

## 6. Operations & Security

### 6.1 Deployment Flow
- **Branches:** `main` (Production), `develop` (Staging).
- **CI/CD:** GitHub Actions for linting, type-checking, and automated testing.
- **Database:** Migrations managed via Supabase CLI; direct console edits are forbidden.

### 6.2 Security Layer
- **RLS (Row Level Security):** Enforced on all tables to ensure users only modify their own data.
- **Rate Limiting:** Enforced on high-frequency actions (Follows, Ships).
- **Data Protection:** EXIF stripping for privacy; No regulated financial or health data stored.

---

## 7. Roadmap & Success Criteria

### 7.1 Milestones
- **Phase 1 (MVP):** Focus on the "Core Ship Loop." (WASB ≥ 25).
- **Phase 2 (Beta):** Focus on Retention. Notifications, digest emails, and collaboration tools.
- **Phase 3 (v1.0):** Focus on Trust. Verified milestone metrics via Stripe/GitHub APIs.

### 7.2 Kill Criteria
Project will be re-evaluated or stopped if:
- Signal-to-Noise Ratio drops below 50%.
- WASB remains < 25 after 8 weeks post-launch.
- Unit economics fail (Storage costs > $10/active user).

---

*Last Updated: February 12, 2026*
