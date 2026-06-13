/**
 * k6 Load & Stress Test – Social Network API Gateway
 * =====================================================
 * Target : http://localhost:8080  (Spring Cloud Gateway)
 *
 * Stages:
 *   Stage 1 – Ramp-up   : 0 → 500 VUs over  30 s
 *   Stage 2 – Sustain   : 500 VUs for         1 m
 *   Stage 3 – Ramp-down : 500 → 0 VUs over   30 s
 *
 * Scenarios:
 *   A – Read Heavy  : GET /api/v1/recommendations/explore   (feed fetching)
 *   B – Write Heavy : POST /api/v1/media/posts              (post creation → Kafka + AI)
 *
 * Thresholds:
 *   • p(95) of all requests < 500 ms
 *   • Error rate < 1 %
 *
 * Usage:
 *   1. Install k6  →  https://k6.io/docs/getting-started/installation/
 *   2. Set the AUTH_TOKEN env var (or replace the placeholder below):
 *        $env:AUTH_TOKEN="eyJhbGciOiJIUzI1NiIsIn..."    (PowerShell)
 *        export AUTH_TOKEN="eyJhbGciOiJIUzI1NiIsIn..."  (bash)
 *   3. Run:
 *        k6 run infra/load-test.js
 *      OR with a custom base URL:
 *        k6 run -e BASE_URL=https://staging.api.example.com infra/load-test.js
 */

import http from "k6/http";
import { check, sleep, group } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";
import { FormData } from 'https://jslib.k6.io/formdata/0.0.2/index.js';

// ──────────────────────────────────────────────────────────────────────────────
// Configuration
// ──────────────────────────────────────────────────────────────────────────────

/** Base URL of the API Gateway. Override with -e BASE_URL=… */
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

/**
 * Authorization Bearer token.
 * Provide via the AUTH_TOKEN environment variable.
 * Replace the placeholder value for local quick-runs.
 *
 *   k6 run -e AUTH_TOKEN="<your_jwt_here>" infra/load-test.js
 */
const AUTH_TOKEN = __ENV.AUTH_TOKEN || "REPLACE_ME_WITH_A_VALID_JWT_TOKEN";

// ──────────────────────────────────────────────────────────────────────────────
// Custom metrics
// ──────────────────────────────────────────────────────────────────────────────

const scenarioAErrors = new Counter("scenario_a_errors");
const scenarioBErrors = new Counter("scenario_b_errors");
const scenarioADuration = new Trend("scenario_a_duration", true);
const scenarioBDuration = new Trend("scenario_b_duration", true);
const errorRate = new Rate("error_rate");

// ──────────────────────────────────────────────────────────────────────────────
// k6 options – stages + thresholds
// ──────────────────────────────────────────────────────────────────────────────

export const options = {
  /**
   * Stage 1 – Ramp-up   : 0 → 500 VUs in 30 s
   * Stage 2 – Sustain   : 500 VUs for    1 m
   * Stage 3 – Ramp-down : 500 → 0 VUs in 30 s
   */
  stages: [
    { duration: "30s", target: 500 }, // Stage 1: ramp-up
    { duration: "1m", target: 500 }, // Stage 2: sustain
    { duration: "30s", target: 0 }, // Stage 3: ramp-down
  ],

  thresholds: {
    // 95th percentile of ALL requests must finish within 500 ms
    http_req_duration: ["p(95)<500"],

    // Overall error rate (non-2xx responses) must stay below 1 %
    error_rate: ["rate<0.01"],

    // Per-scenario p(95) thresholds
    scenario_a_duration: ["p(95)<500"],
    scenario_b_duration: ["p(95)<500"],
  },
};

// ──────────────────────────────────────────────────────────────────────────────
// Shared request headers
// ──────────────────────────────────────────────────────────────────────────────

const commonHeaders = {
  Authorization: `Bearer ${AUTH_TOKEN}`,
  Cookie: `jwt=${AUTH_TOKEN}`,
  "Content-Type": "application/json",
  Accept: "application/json",
};

// ──────────────────────────────────────────────────────────────────────────────
// Helper utilities
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Returns true when the HTTP response is considered successful (2xx).
 */
function isSuccess(res) {
  return res.status >= 200 && res.status < 300;
}

/**
 * Records an error metric whenever the response is not 2xx.
 */
function trackError(res, counterMetric) {
  if (!isSuccess(res)) {
    counterMetric.add(1);
    errorRate.add(1);
  } else {
    errorRate.add(0);
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// Scenario A – Read Heavy: GET /api/v1/recommendations/explore
// ──────────────────────────────────────────────────────────────────────────────

function scenarioA() {
  group("Scenario A – Read Heavy: Explore Feed", () => {
    // Simulate pagination; pick a random page number (0–9) to spread load
    const page = Math.floor(Math.random() * 10);
    const size = 20;
    const url = `${BASE_URL}/api/v1/media/posts/me?page=${page}&size=${size}`;

    const start = Date.now();
    const res = http.get(url, { headers: commonHeaders, tags: { name: "explore_feed" } });
    scenarioADuration.add(Date.now() - start);

    // Assertions
    const ok = check(res, {
      "A: status is 200 OK": (r) => r.status === 200,
      "A: response body is not empty": (r) => r.body && r.body.length > 0,
      "A: content-type is JSON": (r) =>
        r.headers["Content-Type"] && r.headers["Content-Type"].includes("application/json"),
      "A: response time < 500 ms": (r) => r.timings.duration < 500,
    });

    trackError(res, scenarioAErrors);

    // Simulate realistic think time between feed refreshes (0.5 – 2 s)
    sleep(Math.random() * 1.5 + 0.5);
  });
}

// ──────────────────────────────────────────────────────────────────────────────
// Scenario B – Write Heavy: POST /api/v1/media/posts
// ──────────────────────────────────────────────────────────────────────────────

/** Sample post contents used to vary the Kafka/AI payload */
const POST_CONTENTS = [
  "Just had a great morning jog! Feeling alive 🏃",
  "Check out this amazing sunset photo from yesterday 🌅",
  "Anyone else excited for the upcoming tech conference? 🖥️",
  "Sharing my favourite recipe: avocado toast with a twist 🥑",
  "Looking for book recommendations – drop your favourites below 📚",
  "Team lunch today was absolutely delicious 🍜",
  "Final exams done! Time to celebrate 🎉",
  "New article published on my blog – link in bio 📝",
];

function scenarioB() {
  group("Scenario B – Write Heavy: Create Post", () => {
    const content = POST_CONTENTS[Math.floor(Math.random() * POST_CONTENTS.length)];
    const url = `${BASE_URL}/api/v1/media/posts/create`;

    const payload = {
      content: content,
      accessModifier: "PUBLIC",
      media: http.file("fake_image_data", "dummy.jpg", "image/jpeg") 
    };

    const reqHeaders = {
      Authorization: `Bearer ${AUTH_TOKEN}`,
      Cookie: `jwt=${AUTH_TOKEN}`,
      Accept: "application/json",
    };

    const start = Date.now();
    const res = http.post(url, payload, {
      headers: reqHeaders,
      tags: { name: "create_post" },
    });
    scenarioBDuration.add(Date.now() - start);

    if (res.status !== 200 && res.status !== 201) {
       console.log(`[LỖI B] Status: ${res.status}, Body: ${res.body}`);
    }

    check(res, {
      "B: status is 200 or 201 Created": (r) => r.status === 200 || r.status === 201,
      "B: response time < 500 ms": (r) => r.timings.duration < 500,
    });

    trackError(res, scenarioBErrors);
    sleep(Math.random() * 2 + 1);
  });
}

// ──────────────────────────────────────────────────────────────────────────────
// Default function – interleave both scenarios
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Each VU iteration runs Scenario A (read) once and Scenario B (write) once,
 * producing a ~70 % read / ~30 % write split on average due to the different
 * think times.
 *
 * To simulate a read-only or write-only VU, comment out one of the calls.
 */
export default function () {
  // 70 % probability → read scenario; 30 % → write scenario
  if (Math.random() < 0.7) {
    scenarioA();
  } else {
    scenarioB();
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// Lifecycle hooks
// ──────────────────────────────────────────────────────────────────────────────

export function setup() {
  console.log("=================================================");
  console.log("  Social Network – k6 Load & Stress Test");
  console.log(`  Target : ${BASE_URL}`);
  console.log(`  Auth   : ${AUTH_TOKEN === "REPLACE_ME_WITH_A_VALID_JWT_TOKEN" ? "⚠️  Using placeholder token!" : "✅  Custom token set"}`);
  console.log("=================================================");

  // Optional: call a health/readiness endpoint to verify the gateway is reachable
  const healthRes = http.get(`${BASE_URL}/actuator/health`, {
    tags: { name: "health_check" },
  });

  if (healthRes.status !== 200) {
    console.warn(
      `⚠️  Gateway health check returned ${healthRes.status}. Proceeding anyway…`
    );
  } else {
    console.log("✅  Gateway is reachable.");
  }
}

export function teardown(data) {
  console.log("=================================================");
  console.log("  Load test complete.");
  console.log(`  Scenario A errors : ${scenarioAErrors.value || 0}`);
  console.log(`  Scenario B errors : ${scenarioBErrors.value || 0}`);
  console.log("=================================================");
}
