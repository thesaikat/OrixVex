const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validSignalPayload(userId, now = new Date()) {
  return {
    userId,
    symbol: "BTCUSDT",
    displayPair: "BTC/USD",
    timeframe: "15m",
    tradingStyle: "Scalp 5m/15m",
    signal: "STRONG BUY",
    confidence: 86,
    marketRegime: "Bullish Momentum",
    entryLow: 91200.0,
    entryHigh: 91450.0,
    stopLoss: 90650.0,
    takeProfit1: 92250.0,
    takeProfit2: 92950.0,
    takeProfit3: 94000.0,
    riskRewardRatio: "1 : 2.6",
    indicatorConfluence: [
      "EMA 20 > EMA 50 > EMA 200 bullish alignment",
      "RSI(14) at 61.4 showing strong upward momentum",
      "Price trading +0.42% above session VWAP",
      "Supertrend(10,3) in active bullish trend state"
    ],
    invalidationCondition: "4H candle close below $90,650 invalidates bullish momentum structure.",
    status: "ACTIVE",
    createdAt: now,
    updatedAt: now,
  };
}

function validSettingsPayload(userId, now = new Date()) {
  return {
    userId,
    strategyProfile: "Balanced",
    defaultTimeframe: "15m",
    accountBalanceUsd: 25000.0,
    riskPercentPerTrade: 1.0,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user: cannot read or write user settings or signals", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("signals").get()
  );
});

test("Authenticated user: can create and read their own user settings", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validSettingsPayload(ALICE_UID))
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: cannot read or modify another user's settings or signals", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(ALICE_UID).set(validSettingsPayload(ALICE_UID, now));
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("signals")
      .doc("sig_1")
      .set(validSignalPayload(ALICE_UID, now));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_1").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("signals")
      .where("userId", "==", ALICE_UID)
      .get()
  );
});

test("Authenticated user: can create, query, update status, and delete their own saved signals", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const sigRef = aliceDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_1");
  const initialNow = new Date(Date.now() - 5000);
  await assertSucceeds(sigRef.set(validSignalPayload(ALICE_UID, initialNow)));

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("signals")
      .where("userId", "==", ALICE_UID)
      .get()
  );

  await assertSucceeds(
    sigRef.update({
      status: "TP1_HIT",
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(sigRef.delete());
});

test("Shadow update and invalid enum values are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const sigRef = aliceDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_bad");
  await assertFails(
    sigRef.set({
      ...validSignalPayload(ALICE_UID),
      signal: "MOON_SHOT",
    })
  );
  await assertFails(
    sigRef.set({
      ...validSignalPayload(ALICE_UID),
      ghostField: true,
    })
  );
});
