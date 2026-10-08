# FleetCheck: Quarterly Random Selection (Part 4, Option A)

Selects a fair, reproducible, once-per-quarter random sample of a company's active vehicles for inspection.

## Problem

Given a company's active vehicles, a percentage and a quarter, choose which vehicles to inspect. Every eligible vehicle must have an equal chance. An auditor must be able to reproduce the result later. Last quarter's picks stay eligible. Running it twice for the same company and quarter must not produce a second, different selection. It has to work for 20 vehicles and for 200,000.

## Requirements → implementation map

| # | Requirement | Where |
|---|---|---|
| 1 | Input: active vehicles, percentage, quarter | `SelectionRequest(companyId, quarter, percentage, vehicleIds)` |
| 2 | Equal chance for every vehicle | HMAC-SHA256 score per vehicle, sort, take first K (`QuarterlySelector`) |
| 3 | Auditor can reproduce | Pure function of inputs + key; result stores `algorithmVersion`, `keyId`, `inputFingerprint` |
| 4 | Last quarter's picks still eligible | Selection history is not an input; the quarter is hashed, so each quarter is an independent draw |
| 5 | No second, different selection | `QuarterlySelectionService` + `SelectionRepository.saveIfAbsent` keyed by `(companyId, quarter)` |
| 6 | 20 to 200,000 vehicles | O(N log N) full sort; tested at both sizes |

## Design

```
SelectionRequest
   │ validate      null/blank/duplicate IDs, percentage in [0,100], Quarter "2026-Q4"
   ▼
canonicalise       each field → 4-byte big-endian length ‖ UTF-8 bytes
   │
   ▼
HMAC score         score = HMAC-SHA256(auditKey, enc(version, companyId, quarter, vehicleId))
   │
   ▼
sort               by score (unsigned bytes), tie-break on vehicleId
   │
   ▼
take K             K = ceil(N × p / 100), exact BigDecimal; output sorted by vehicleId
   │
   ▼
fingerprint        SHA-256(enc(version, keyId, companyId, quarter, percentage, sorted IDs…))
   │
   ▼
saveIfAbsent       one selection per (companyId, quarter); first writer wins
```

Two layers, on purpose:

- **`QuarterlySelector`** is the pure algorithm, and it is **deterministic**: the same inputs and key always give the same output. It has no storage.
- **`QuarterlySelectionService`** provides **idempotency**: one selection per company and quarter, even if the fleet or percentage changes, or two requests race.

`QuarterlySelector.rank(request)` returns the full rank order. The selection is its first K, so auditors can see it, and it's where replacement vehicles would come from.

## Fairness

Assuming HMAC-SHA256 behaves as a pseudo-random function, the ranking is a uniformly random permutation of the eligible set. Every K-subset is equally likely, so each vehicle's chance of selection is K/N. Input order and selection history are not inputs to the score.

`FairnessSanityTest` checks frequencies over 1,000 fixed companies. It's a sanity check against gross bias, not a proof.

## Reproducibility

An auditor needs:

1. **Algorithm version**: `HMAC-SHA256-RANK-v1`, stored in the result.
2. **The key**, fetched from escrow by the `keyId` stored in the result. The key itself is never stored with the result.
3. **companyId, quarter, percentage, and the exact eligible vehicle-ID set.**

They recompute `inputFingerprint` and verify that it matches the stored fingerprint, providing a deterministic integrity check that the supplied audit inputs match the original selection inputs. Then they re-run the algorithm. `GoldenSelectionTest` pins one selection and was cross-checked against an independent Python implementation of the description above. Exact byte layout:

- `enc(f1, …, fn)` = for each field: `uint32_be(len(utf8(f)))` ‖ `utf8(f)`
- Score message: `enc("HMAC-SHA256-RANK-v1", companyId, "2026-Q4", vehicleId)`
- Fingerprint: `SHA-256(enc(version, keyId, companyId, quarter, pct) ‖ enc(id₁) ‖ … ‖ enc(idₙ))`, with IDs sorted by UTF-16 code unit (Java `String.compareTo`, JS default `sort`) and `pct` written as `stripTrailingZeros().toPlainString()`, so `25`, `25.0` and `25.00` all give `"25"`.

## Idempotency

| Stored for (company, quarter)? | Request fingerprint | Behaviour |
|---|---|---|
| No | n/a | Compute, `saveIfAbsent`. If another caller won the race, return **their** result |
| Yes | Same (any input order, any percentage scale) | Return the stored result unchanged |
| Yes | Different (percentage, vehicle set, key, or version) | Throw `SelectionConflictException` carrying the stored result. Never recompute or replace |

In production the store is a table with `UNIQUE(company_id, quarter)`. In one transaction, run `INSERT … ON CONFLICT (company_id, quarter) DO NOTHING`, then `SELECT` the row, and compare fingerprints.

**Design note:** `keyId` is part of the fingerprint. Otherwise, a re-run with a different key would produce the same fingerprint and would not raise a conflict.

## Previous-quarter eligibility

The score depends only on `(version, company, quarter, vehicle)`. Past selections are never read, so a vehicle picked in Q1 has the same K/N chance in Q2. A test pins a vehicle (`VH-000018`) that is selected in both quarters.

## Edge case: rounding the count

The brief says "a percentage" of the fleet, but 25% of 3 vehicles is 0.75. **K = ceil(N × p / 100)**, computed in exact `BigDecimal` arithmetic:

| Fleet | % | K |
|---|---|---|
| 20 | 25 | 5 |
| 3 | 25 | 1 (0.75 rounds up) |
| 5 | 10 | 1 |
| any | 0 | 0 |
| N | 100 | N |
| 0 | any | 0 (empty result, not an error) |

Ceiling means any positive percentage of a non-empty fleet selects at least one vehicle, so a surprise inspection is never silently empty. Exact arithmetic matters because ceiling amplifies tiny errors: with doubles, `1000 × 16.1 / 100 = 161.00000000000003`, which would select 162 instead of 161.

Other validation decisions:

- **Vehicle IDs are opaque, exact-match strings.** No trimming or case folding, so `"V1"`, `"v1"` and `" V1"` are different vehicles. Null, blank or malformed-Unicode IDs are rejected with `IllegalArgumentException`, and so are duplicates. The error names the offending ID or index. Duplicates are never silently de-duplicated, because a duplicate signals an upstream bug.
- **Callers pass active vehicles only.** The selector does not filter.
- **Percentage** must be in [0, 100]; null is rejected. **Quarter** is `"YYYY-Qn"` with a four-digit year. It is parsed strictly and never formatted through a locale.
- **Audit key** must be at least 32 bytes (RFC 2104), is defensively copied, and prints as `<redacted>`.

## Complexity

N = eligible vehicles. **Time O(N log N)**: one HMAC per vehicle, then a full sort. **Space O(N)**: one 32-byte score per vehicle (about 15 MB at 200,000). A top-K heap would be O(N log K), but a full sort is simpler to audit, and 200k is small. The 200k test runs in well under a second.

## Running

Requires JDK 21 (`JAVA_HOME` or `java` on `PATH`). The Maven Wrapper downloads Maven itself.

```bash
./mvnw test
```

## Project structure

```
src/main/java/com/fleetcheck/selection/
  QuarterlySelector.java            pure algorithm: score, rank, take K, result
  QuarterlySelectionService.java    idempotency: one selection per (company, quarter)
  SelectionRepository.java          findByCompanyAndQuarter, saveIfAbsent
  InMemorySelectionRepository.java  ConcurrentHashMap.putIfAbsent implementation
  SelectionConflictException.java   different inputs for an existing selection
  SelectionRequest.java             validated inputs
  SelectionResult.java              immutable, auditable output
  AuditKey.java                     keyId + secret material (redacted toString)
  Quarter.java                      "2026-Q4"
  SelectionCount.java               K = ceil(N × p / 100)
  InputFingerprint.java             SHA-256 over canonical inputs
  CanonicalEncoding.java            length-prefixed UTF-8
  ScoredVehicle.java                rank comparator (unsigned bytes, then vehicleId)
src/test/java/com/fleetcheck/selection/   unit tests per component, plus golden, fairness and concurrency tests
```

## Production considerations

- **PostgreSQL** table `quarterly_selections` with `UNIQUE(company_id, quarter)`. Write with `INSERT … ON CONFLICT DO NOTHING` + `SELECT` in one transaction, so multiple instances agree on a single winner.
- **Key in AWS Secrets Manager / KMS**, loaded by `keyId`. Rotate by issuing a new `keyId` for new quarters, and keep retired keys readable for audits.
- **Keep old algorithm versions runnable.** A change in output means a new `ALGORITHM_VERSION`, never an edit to v1. The golden test enforces this.
- **Store the eligible-set snapshot** (the vehicle IDs) with each selection, so an audit doesn't depend on reconstructing "active on that day".
- **Append-only audit log** of selection requests, conflicts and who triggered them.
- **Publish a commitment** (SHA-256 of the key) before each quarter, so the regulator can check the key wasn't chosen after seeing the result.
## Why Java

Java is where I write the most rigorous, well-tested code fastest, and the algorithm (HMAC, sort, SHA-256 over a documented byte layout) ports directly to NestJS via `crypto.createHmac` and `Buffer.compare`.

## AI assistance

Developed with AI assistance (Claude Code), then human-reviewed and tested.
