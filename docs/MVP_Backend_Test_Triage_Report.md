# 1. Executive Summary

ShipSpace backend is now **READY WITH ACCEPTED NON-BLOCKING TEST DEBT** for the MVP backend lane.

This sprint closed the two MVP-blocking certification lanes from the previous report:

- `EngagementServiceTest` now passes against the current atomic counter behavior.
- `ShipCreationIntegrationTest` now passes against a real local MongoDB path with test-profile isolation.

The full integration bucket still fails and must not be treated as a default release gate yet. The remaining failures are classified as integration-harness debt: Docker/Testcontainers discovery, stale test profile setup, Redis fixture gaps, and stale scoring fixtures. These are documented below and should move into a separate integration/CI lane instead of blocking the fast MVP backend gate.

# 2. Commands Run

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\mvnw.cmd -q -Dtest=EngagementServiceTest test
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\mvnw.cmd -Dtest=*ServiceTest test
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\mvnw.cmd -q -Dtest=ShipCreationIntegrationTest test
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\mvnw.cmd -Dtest=*IntegrationTest test
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\mvnw.cmd -q -DskipTests compile
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; .\mvnw.cmd -q '-Dtest=AuthServiceRbacTest,BannedUserWriteFilterTest,MongoIndexInitializerTest,MultibaseServiceTest,GitHubOAuthServiceTest,JwtUtilTest,UserInterestProfileRepositoryTest' test
npm.cmd run lint
npm.cmd run build
powershell -ExecutionPolicy Bypass -File .\ops\mvp-smoke-test.ps1 -SkipBuildChecks
```

Previously verified and still accepted from the prior pass:

```powershell
.\mvnw.cmd -Dtest=*ControllerTest test
.\mvnw.cmd -Dtest=*RepositoryTest test
.\mvnw.cmd -q '-Dtest=FeedServiceTest' test
```

# 3. EngagementServiceTest Result

**PASS**

`EngagementServiceTest` now validates the current production behavior:

- like creation persists a `Like` and calls `ShipCountRepository.incrementLikeCount(shipId, 1)`;
- unlike deletes the like and decrements through `ShipCountRepository`;
- duplicate-like race handling deletes and decrements atomically;
- zero-count responses clamp non-negative values;
- comment creation saves the comment and increments comment count through `ShipCountRepository`;
- paged comment/like reads use `userRepository.findAllById(...)` batch loading;
- comment upvotes use Mongo `findAndModify` rather than stale `commentRepository.save(...)` mutation expectations;
- scoring hooks are still verified for the unqualified-to-qualified transition.

Service bucket result:

```text
Tests run: 259, Failures: 0, Errors: 0, Skipped: 0
```

# 4. ShipCreationIntegrationTest Result

**PASS**

`ShipCreationIntegrationTest` now runs under the `test` profile, uses a unique local Mongo database per JVM run, disables model auto-index creation so `MongoIndexInitializer` remains the only index owner, and relaxes health group membership only for the test context because `searchIndex` is intentionally excluded from the `test` profile.

Covered MVP path:

- authenticated user can create a ship;
- ship is persisted with correct user, project, content, counters, and edit window;
- project `shipCount` is atomically incremented;
- unauthenticated request is rejected;
- non-owner project write is rejected with `403`;
- missing project is rejected with `404`.

Result:

```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
```

# 5. Integration Bucket Result

**FAIL, deferred from default MVP gate**

Full bucket command:

```powershell
.\mvnw.cmd -Dtest=*IntegrationTest test
```

Observed result:

```text
Tests run: 49, Failures: 13, Errors: 29, Skipped: 0
```

Important classification:

- `ShipCreationIntegrationTest`: now passes and is accepted as the MVP ship-creation proof.
- Docker/Testcontainers classes: fail with Docker environment discovery errors in this local execution context.
- `AuthControllerIntegrationTest`: fails because the `test` profile excludes Redis auto-configuration but `OAuthStateService` requires `StringRedisTemplate`.
- `FeedOrderingIntegrationTest`: stale context setup; it does not yet use the same test-profile and unique-Mongo isolation pattern now applied to ship creation.
- `ScoringHooksIntegrationTest`: stale fixture data; upvote scenarios reference `upvoter123` without creating that user under current `EngagementService` existence checks.
- Project/scoring integration failures: stale scoring-hook expectations remain outside the ship-creation MVP lane.

These failures are not accepted as "ignore forever" debt. They are accepted as non-blocking for MVP backend certification because the critical engagement and ship-creation lanes are now independently green and the runtime smoke path passes.

# 6. Fixed Failures

- Repaired `EngagementServiceTest` stale expectations around atomic ship like/comment counters.
- Repaired comment upvote tests to verify Mongo `findAndModify` counter updates.
- Repaired paged engagement read tests for batch user fetching via `findAllById`.
- Repaired stale unlike default stubbing so concurrent delete behavior is tested intentionally.
- Repaired `ShipCreationIntegrationTest` missing `test` profile.
- Repaired `ShipCreationIntegrationTest` fixed Mongo database/index collision by assigning a unique database per test JVM.
- Repaired `ShipCreationIntegrationTest` index ownership mismatch by disabling model auto-index creation for that test.
- Repaired `ShipCreationIntegrationTest` readiness health membership failure with test-only health group membership relaxation.
- Preserved the previous real production config fix: removed the self-referential top-level `SWAGGER_ENABLED` alias from `src/main/resources/application.yml`.

# 7. Remaining Failures

Remaining failures are outside the default MVP backend gate:

- full `*IntegrationTest` bucket still fails because it mixes Testcontainers-required tests, local-Mongo tests, stale fixture tests, and Spring context tests without one consistent integration profile;
- Docker/Testcontainers discovery is unreliable in the current local runner;
- Redis-dependent auth integration tests need either Redis test infrastructure or a test `StringRedisTemplate` fixture;
- feed/scoring integration tests need the same profile/database/index isolation now proven in `ShipCreationIntegrationTest`;
- scoring hook fixtures need explicit user setup for current user existence validation.

# 8. Production-Impacting Defects

Fixed production-impacting defect:

- `SWAGGER_ENABLED` circular placeholder in `application.yml` caused Spring context boot failure under integration conditions.

No new production-impacting defect was confirmed in this sprint:

- `EngagementServiceTest` failures were stale test expectations after atomic counter refactors.
- `ShipCreationIntegrationTest` failures were test harness/profile/database isolation issues.
- Docker/Testcontainers failures are CI/environment readiness issues, not proven runtime defects.

# 9. Accepted Test Debt

Accepted for MVP backend certification:

- full `*IntegrationTest` is not a default gate yet;
- Docker/Testcontainers suites must run only on a runner with known-good Docker access;
- Redis-dependent auth integration coverage needs a dedicated Redis-backed integration lane;
- feed/scoring integration fixtures need a follow-up stabilization pass.

Not accepted:

- silently skipping integration tests;
- removing ship-creation coverage;
- weakening production code to satisfy stale tests.

# 10. CI Gate Recommendation

Default MVP backend gate:

```powershell
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -Dtest=*ServiceTest,*ControllerTest,*RepositoryTest test
.\mvnw.cmd -q '-Dtest=AuthServiceRbacTest,BannedUserWriteFilterTest,MongoIndexInitializerTest,MultibaseServiceTest,GitHubOAuthServiceTest,JwtUtilTest,UserInterestProfileRepositoryTest' test
.\mvnw.cmd -q -Dtest=ShipCreationIntegrationTest test
```

Frontend/runtime gate:

```powershell
cd web
npm.cmd run lint
npm.cmd run build
cd ..
powershell -ExecutionPolicy Bypass -File .\ops\mvp-smoke-test.ps1 -SkipBuildChecks
```

Deferred integration lane:

```powershell
.\mvnw.cmd -Dtest=*IntegrationTest test
```

CI recommendation:

- run the default MVP backend gate on every PR;
- run `ShipCreationIntegrationTest` on every PR with local Mongo or a service container;
- run full `*IntegrationTest` in a separate required-before-release workflow only after Docker/Testcontainers, Redis, and fixture isolation are standardized;
- keep manual MVP checks for real GitHub OAuth, admin bootstrap, banned-user write blocking, upload validation, and Multibase privacy policy until browser/E2E automation exists.

# 11. Final MVP Backend Readiness Decision

**READY WITH ACCEPTED NON-BLOCKING TEST DEBT**

The backend now has green compile, focused MVP gate, service bucket, controller bucket, repository bucket, `FeedServiceTest`, `ShipCreationIntegrationTest`, frontend lint/build, and Docker smoke evidence. The remaining full integration bucket debt is real and documented, but it no longer blocks MVP backend certification because the prior P1/P2 lanes for engagement behavior and ship creation have been made trustworthy.
