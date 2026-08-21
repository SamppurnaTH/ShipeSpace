# KREVO (ShipSpace) - Manual QA Release-Gate Runbook

**Purpose:** Close the six gates identified in `docs/MVP_Tracking_Document.md` Section 18 with recorded, dated, evidence-backed results, not prose claims. This runbook does not replace the tracker. Once every gate below is checked off with evidence, its results feed back into Section 18 to update the final verdict.

**Execution rule:** If a step cannot be completed, mark it `BLOCKED` or `FAIL` with a reason. Never skip a row silently, and never mark `PASS` without filling the evidence fields.

## 0. Preflight Environment Checks

Run these before starting any gate. If any fail, fix them first. Do not proceed into release gates with a broken environment.

| # | Check | Command / Location | Expected | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| 0.1 | Backend container healthy | `docker compose ps` | `backend` shows healthy | [ ] PASS [ ] FAIL | |
| 0.2 | Frontend container running | `docker compose ps` | `frontend` running | [ ] PASS [ ] FAIL | |
| 0.3 | MongoDB running | `docker compose ps` | `mongo` running | [ ] PASS [ ] FAIL | |
| 0.4 | Redis running | `docker compose ps` | `redis` running | [ ] PASS [ ] FAIL | |
| 0.5 | Backend health endpoint | `curl http://localhost:<port>/health` or smoke script | HTTP 200 | [ ] PASS [ ] FAIL | |
| 0.6 | Frontend shell loads | Open app URL in browser | Page renders, no console fatal errors | [ ] PASS [ ] FAIL | |
| 0.7 | `GITHUB_CLIENT_ID` set | Env inspection | Non-empty | [ ] PASS [ ] FAIL | |
| 0.8 | `GITHUB_CLIENT_SECRET` set | Env inspection | Non-empty | [ ] PASS [ ] FAIL | |
| 0.9 | `GITHUB_REDIRECT_URI` matches actual test URL | Env inspection vs. browser address bar | Match | [ ] PASS [ ] FAIL | Known risk: currently set to `localhost:3005`; confirm this is where testing occurs. |
| 0.10 | `ADMIN_GITHUB_IDS` contains Admin test account numeric GitHub ID | Env inspection | ID present | [ ] PASS [ ] FAIL | Current value: `153720530,257914593`; confirm which is yours. |
| 0.11 | `MULTIBASE_API_URL` reachable from running environment | `curl <MULTIBASE_API_URL>` or provider dashboard | Reachable, not `localhost` unless testing local-only | [ ] PASS [ ] FAIL | |
| 0.12 | `MULTIBASE_API_KEY` set | Env inspection | Non-empty | [ ] PASS [ ] FAIL | Known gap: currently blank. |
| 0.13 | `JWT_SECRET` set | Env inspection | Non-empty | [ ] PASS [ ] FAIL | |
| 0.14 | `CORS_ALLOWED_ORIGINS` includes frontend URL | Env inspection | Match | [ ] PASS [ ] FAIL | |
| 0.15 | `DEMO_MODE` is `false` unless intentionally seeding demo data | Env inspection | `false` | [ ] PASS [ ] FAIL | |

**Preflight sign-off:** [ ] All checks PASS - proceed to Section 1. [ ] Blockers found - log in Section 8 and resolve before continuing.

## 1. Required Accounts and Roles

| Role | Requirement | GitHub account handle | Numeric GitHub ID | Confirmed in `ADMIN_GITHUB_IDS`? | Ready? |
| --- | --- | --- | --- | --- | --- |
| Admin tester (Account A) | Real GitHub account, ID present in `ADMIN_GITHUB_IDS` | | | [ ] Yes [ ] No | [ ] |
| Normal user tester (Account B) | Real GitHub account, ID absent from `ADMIN_GITHUB_IDS` | | | [ ] Yes (must be No) | [ ] |
| Second normal user (Account C) | Real GitHub account, distinct from B, used only for the two-account social loop | | | [ ] Yes (must be No) | [ ] |

Do not proceed to Gates A-F until all three rows show `Ready`.

## 2. Gate Order

Execute in this order. Later gates depend on earlier ones. For example, banned-write blocking cannot be tested honestly until both Admin and normal User login have been verified.

1. Gate A - Real GitHub OAuth Login
2. Gate B - Admin Role Isolation
3. Gate C - Banned-User Write Blocking
4. Gate D - Upload File Matrix
5. Gate E - Two-Account Social/Collaboration/Notification Loop
6. Gate F - Responsive Visual QA

## 3. Evidence Field Legend

Every gate row must be completed with:

- **Tester:** name of person executing
- **Date:** ISO format, e.g. `2026-08-25`
- **Account used:** Account A, B, C, or logged out
- **Browser:** e.g. Chrome 128, Edge 128, Safari 17
- **Result:** PASS, FAIL, or BLOCKED
- **Notes:** observed result, screenshot filename if applicable, or reason for BLOCKED

## Gate A - Real GitHub OAuth Login

| # | Step | Expected | Tester | Date | Account | Browser | Result | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A.1 | First-time login with Account B, never logged in before | Reaches authenticated home, role = USER only | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| A.2 | Log out, then log back in with Account B | Reaches authenticated home, no duplicate account created, existing profile data intact | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| A.3 | First-time login with Account A | Reaches authenticated home, role includes ADMIN | | | A | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| A.4 | Cancel OAuth flow mid-way or deny GitHub authorization | Clear failure message, retry available, user not left in broken state | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| A.5 | Logout | Tokens cleared, redirected to public home, protected routes now redirect/block | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| A.6 | Visit protected route while logged out: `/feed`, `/settings`, `/ships/new` | Redirect to auth or public home, no data leak | | | logged out | | [ ] PASS [ ] FAIL [ ] BLOCKED | |

**Gate A verdict:** [ ] PASS - all rows pass. [ ] PARTIAL. [ ] BLOCKED - see Section 8.

## Gate B - Admin Role Isolation

| # | Step | Expected | Tester | Date | Account | Browser | Result | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| B.1 | Log in as Account A, visit `/admin` | Dashboard loads with real stats | | | A | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| B.2 | Log in as Account B, visit `/admin` directly | Forbidden UX shown, no admin data rendered | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| B.3 | As Account B, call an admin API directly, e.g. from browser devtools or manual request | HTTP 403, no data returned | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| B.4 | As Account A, navigate `/admin/users`, `/admin/reports`, `/admin/audit-logs` | Each loads with real data, no fake/placeholder content | | | A | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| B.5 | As Account A, attempt to remove own `ADMIN` role via roles endpoint if UI or manual request exposes it | Request is rejected with a clean forbidden response; admin role remains unchanged | | | A | | [ ] PASS [ ] FAIL [ ] BLOCKED | Phase 0.1 guard should protect this path. |

**B.5 note:** The CEO decision is now resolved: self-demotion should be guarded before freeze. The manual gate should verify the guard in a real admin session or direct manual request.

Observed result: ________________________________________________

**Gate B verdict:** [ ] PASS. [ ] PARTIAL. [ ] BLOCKED.

## Gate C - Banned-User Write Blocking

| # | Step | Expected | Tester | Date | Account | Browser | Result | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| C.1 | As Account A, ban Account B via `/admin/users/{id}` | Ban persists, confirmation shown | | | A | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| C.2 | As Account A, check `/admin/audit-logs` for `ADMIN_USER_BANNED` entry | Entry present with correct actor, target, timestamp | | | A | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| C.3 | As Account B, now banned, confirm reads still work: browse feed, view profile | Reads succeed normally | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| C.4 | As Account B, confirm logout still works | Logout succeeds, tokens cleared | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| C.5 | As Account B, banned, attempt to create project, like, comment, follow, upload | All blocked with clear banned-user messaging, no partial writes | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| C.6 | As Account A, unban Account B | Unban persists | | | A | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| C.7 | As Account A, check `/admin/audit-logs` for `ADMIN_USER_UNBANNED` entry | Entry present | | | A | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| C.8 | As Account B, unbanned, retry one write action from C.5 | Write now succeeds | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |

**Gate C verdict:** [ ] PASS. [ ] PARTIAL. [ ] BLOCKED.

## Gate D - Upload File Matrix

Requires Preflight 0.11 and 0.12 to pass first: Multibase reachable and API key set. If either failed, this entire gate is BLOCKED. Do not attempt partial testing.

| # | File type | Expected | Tester | Date | Account | Browser | Result | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| D.1 | Valid PNG | Accepted, renders in ship | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| D.2 | Valid JPEG | Accepted, renders in ship | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| D.3 | Valid GIF | Accepted, renders in ship | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| D.4 | Valid WebP | Accepted, renders in ship | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| D.5 | Oversized file above configured limit | Rejected cleanly, clear error, ship creation still succeeds without image | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| D.6 | Invalid MIME type, e.g. `.txt` renamed to look like upload | Rejected cleanly | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| D.7 | Renamed executable, e.g. `.exe` renamed to `.png` | Rejected by content validation, not extension-only validation | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| D.8 | Corrupt or truncated image file | Rejected cleanly, no server error leaked to UI | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| D.9 | Confirm Multibase delivery does not expose private or unintended data | No unauthorized access path found | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |

**Gate D verdict:** [ ] PASS. [ ] PARTIAL. [ ] BLOCKED.

## Gate E - Two-Account Social / Collaboration / Notification Loop

Uses Account B and Account C.

| # | Step | Expected | Tester | Date | Account | Browser | Result | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| E.1 | B creates a project and publishes a ship | Ship visible on B profile and in feeds | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| E.2 | C follows B | Follow succeeds, B follower count updates | | | C | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| E.3 | B sees C in followers and gets FOLLOW notification | Notification appears, unread badge increments, navigates to `/profile/{C}` | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| E.4 | B ship appears in C Following feed | Ship visible in C following feed | | | C | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| E.5 | C likes B ship | Like registers, count updates | | | C | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| E.6 | B gets LIKE notification and clicks it | Notification correct, navigation goes to ship, notification marked read | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| E.7 | C comments on B ship | Comment appears and persists after reload | | | C | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| E.8 | B gets COMMENT notification | Notification correct, navigates to ship | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| E.9 | C requests collaboration on B project | Request created, visible in C Sent list as PENDING | | | C | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| E.10 | B sees Incoming request and gets COLLABORATION_REQUESTED notification | Notification correct, navigates to `/collaborations` | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| E.11 | B accepts the request | Status flips to ACCEPTED on both sides | | | B | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| E.12 | C gets COLLABORATION_ACCEPTED notification | Notification correct, navigates to `/collaborations`, ShipScore awarded to C | | | C | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| E.13 | C unfollows B | Unfollow succeeds, follower count updates, B ships disappear from C Following feed | | | C | | [ ] PASS [ ] FAIL [ ] BLOCKED | |
| E.14 | Mark all notifications read | Unread badge clears to zero | | | B or C | | [ ] PASS [ ] FAIL [ ] BLOCKED | |

**Gate E verdict:** [ ] PASS. [ ] PARTIAL. [ ] BLOCKED.

## Gate F - Responsive Visual QA

Check at 390px mobile, 768px tablet, 1024px small desktop, and 1440px desktop. No clipping, overlapping elements, unreachable controls, or broken navigation at any width.

| # | Surface | 390px | 768px | 1024px | 1440px | Tester | Date | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| F.1 | Public landing page | [ ] | [ ] | [ ] | [ ] | | | |
| F.2 | Auth page | [ ] | [ ] | [ ] | [ ] | | | |
| F.3 | Authenticated home / feed | [ ] | [ ] | [ ] | [ ] | | | |
| F.4 | Ship detail page | [ ] | [ ] | [ ] | [ ] | | | |
| F.5 | Project list / detail | [ ] | [ ] | [ ] | [ ] | | | |
| F.6 | Profile page | [ ] | [ ] | [ ] | [ ] | | | |
| F.7 | Notifications page | [ ] | [ ] | [ ] | [ ] | | | |
| F.8 | Collaborations page | [ ] | [ ] | [ ] | [ ] | | | |
| F.9 | People / discovery page | [ ] | [ ] | [ ] | [ ] | | | |
| F.10 | Settings page | [ ] | [ ] | [ ] | [ ] | | | |
| F.11 | Admin dashboard | [ ] | [ ] | [ ] | [ ] | | | |
| F.12 | Admin users list / detail | [ ] | [ ] | [ ] | [ ] | | | |
| F.13 | Admin reports | [ ] | [ ] | [ ] | [ ] | | | |
| F.14 | Admin audit logs | [ ] | [ ] | [ ] | [ ] | | | |

**Gate F verdict:** [ ] PASS. [ ] PARTIAL. [ ] BLOCKED.

## 8. Blocker Log

Log every FAIL or BLOCKED result here with enough detail for the next engineering pass to act on. Use one row per distinct issue.

| ID | Gate/Step | Description | Severity (Blocker / Non-blocking) | Logged by | Date | Status | Resolution |
| --- | --- | --- | --- | --- | --- | --- | --- |
| BLK-001 | | | | | | [ ] Open [ ] Fixed [ ] Deferred | |
| BLK-002 | | | | | | [ ] Open [ ] Fixed [ ] Deferred | |
| BLK-003 | | | | | | [ ] Open [ ] Fixed [ ] Deferred | |

## 9. Final Sign-Off Table

Only complete this once every gate above has a verdict recorded.

| Gate | Verdict | Signed off by | Date |
| --- | --- | --- | --- |
| Preflight (Section 0) | [ ] PASS [ ] BLOCKED | | |
| A - GitHub OAuth | [ ] PASS [ ] PARTIAL [ ] BLOCKED | | |
| B - Admin Role Isolation | [ ] PASS [ ] PARTIAL [ ] BLOCKED | | |
| C - Banned-User Write Blocking | [ ] PASS [ ] PARTIAL [ ] BLOCKED | | |
| D - Upload File Matrix | [ ] PASS [ ] PARTIAL [ ] BLOCKED | | |
| E - Two-Account Social/Collab/Notification Loop | [ ] PASS [ ] PARTIAL [ ] BLOCKED | | |
| F - Responsive Visual QA | [ ] PASS [ ] PARTIAL [ ] BLOCKED | | |

**Overall runbook result:** [ ] ALL GATES PASS - ready to update Section 18 to a release-ready verdict. [ ] OPEN BLOCKERS REMAIN - see Section 8.

## 10. Section 18 Update Criteria

Use this checklist to decide how to update `docs/MVP_Tracking_Document.md` Section 18 after this runbook is executed.

- **If all six gates show PASS with no open Severity=Blocker items in Section 8:** Update Section 18 verdict to `READY FOR CONTROLLED PRODUCTION BETA`. Move any Severity=Non-blocking open items into the Accepted Non-Blocking Debt table with this runbook's Blocker Log IDs as evidence references.
- **If one or more gates show PARTIAL or BLOCKED, but the cause is environmental rather than a code defect:** Keep Section 18 verdict as `NOT READY`, but update the specific gate evidence to point at this runbook's row/ID instead of the prior generic `BLOCKED` note.
- **If a gate reveals a genuine code defect:** Do not update Section 18 to READY. Log it in Section 8 with Severity=Blocker, then route it back to a narrowly scoped engineering fix pass. Re-run only the affected gate after the fix, then update this runbook and Section 18 together.
- **Do not mark Section 18 READY based on partial gate completion.** All six gates must show a recorded PASS with evidence fields filled in. A gate with unchecked or blank evidence fields is not complete regardless of verbal confidence.
