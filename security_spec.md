# OrixVex Security Specification (Phase 0 TDD)

## 1. Data Invariants
1. **Authentication Mandatory**: No unauthenticated caller may read or write any document under `/users/{userId}` or `/users/{userId}/signals/{signalId}`.
2. **Strict Tenant Isolation**: A user (`request.auth.uid`) may only read, create, update, or delete documents under their own `/users/{userId}` path where `request.auth.uid == userId` and `data.userId == request.auth.uid`.
3. **Immutable Identity & Creation Timestamp**: `userId` and `createdAt` can never be modified after document creation (`incoming().userId == existing().userId && incoming().createdAt == existing().createdAt`).
4. **Strict Schema & Bounds Validation**: Every string, number, enum, list, and timestamp is strictly validated by `isValidUserSettings(data)` and `isValidSavedSignal(data)` on both `create` and `update`.

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Read**: `unauthDb.collection("users").doc("alice").get()` -> Reject
2. **Cross-User Profile Read**: `bobDb.collection("users").doc("alice").get()` -> Reject
3. **Cross-User Signal List**: `bobDb.collection("users").doc("alice").collection("signals").get()` -> Reject
4. **Identity Spoofing on Create**: `aliceDb` writing `userId: "bob"` under `/users/alice/signals/sig1` -> Reject
5. **Shadow / Ghost Field Injection**: `aliceDb` writing `isAdmin: true` to `/users/alice` -> Reject (`hasOnly`)
6. **Immutable Field Tampering**: `aliceDb` updating `createdAt` or `userId` on an existing signal -> Reject
7. **Invalid Enum Value**: `aliceDb` setting `signal: "MOON_NOW"` or `status: "HACKED"` -> Reject
8. **Out-of-Bounds Confidence**: `aliceDb` setting `confidence: 150` or `-10` -> Reject
9. **Resource Poisoning (Oversized String)**: `aliceDb` injecting a 5,000-char `invalidationCondition` -> Reject
10. **Unbounded Array**: `aliceDb` sending 20 items in `indicatorConfluence` -> Reject (`size() <= 8`)
11. **Future Timestamp Spoofing**: `aliceDb` setting `createdAt` in the future (`> request.time`) -> Reject
12. **Value Poisoning on Update**: `aliceDb` updating `status` with an integer `123` instead of valid enum string -> Reject
