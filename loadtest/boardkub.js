// k6 load test for BoardKub — simulates students browsing the board.
//
//   PROFILE=ramp    (default) 0 -> 150 virtual users: watch the HPA add backend Pods
//   PROFILE=steady  constant 30 users for DURATION: used during a rolling update
//                   to prove zero downtime (fails if even one request errors)
//
//   k6 run -e BASE_URL=https://boardkub.example.com loadtest/boardkub.js
//   k6 run -e BASE_URL=... -e PROFILE=steady -e DURATION=4m loadtest/boardkub.js
//
// Read-only on purpose: it never creates posts, so production data stays clean.
// (Opening a post does bump its view counter, which is a realistic DB write.)
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = (__ENV.BASE_URL || 'http://localhost:8082').replace(/\/+$/, '');
const PROFILE = __ENV.PROFILE || 'ramp';
const DURATION = __ENV.DURATION || '4m';
const PEAK = Number(__ENV.PEAK_VUS || 150);

const errors5xx = new Counter('errors_5xx');

const profiles = {
  ramp: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '1m', target: Math.round(PEAK / 3) },
      { duration: '2m', target: PEAK },
      { duration: '5m', target: PEAK },   // hold: HPA needs a few minutes to react
      { duration: '2m', target: 0 },
    ],
    gracefulRampDown: '30s',
  },
  steady: {
    executor: 'constant-vus',
    vus: Number(__ENV.VUS || 30),
    duration: DURATION,
  },
};

export const options = {
  scenarios: { browse: profiles[PROFILE] },
  thresholds: PROFILE === 'steady'
    ? { http_req_failed: ['rate==0'], errors_5xx: ['count==0'] }          // zero downtime
    : { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<1500'] },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'max'],
  userAgent: 'boardkub-loadtest/1.0 (k6)',
};

// Collect board and post ids once, so virtual users browse real pages
export function setup() {
  const boards = http.get(`${BASE}/api/boards`).json();
  if (!Array.isArray(boards) || boards.length === 0) {
    throw new Error(`No boards at ${BASE}/api/boards — is the site up?`);
  }
  const posts = [];
  for (const b of boards) {
    const page = http.get(`${BASE}/api/boards/${b.id}/posts?page=0&size=10`).json();
    (page.items || []).forEach((p) => posts.push(p.id));
  }
  console.log(`profile=${PROFILE} boards=${boards.length} posts=${posts.length} target=${BASE}`);
  return { boardIds: boards.map((b) => b.id), postIds: posts };
}

const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];
const SEARCH_WORDS = ['ทุน', 'สอบ', 'cloud', 'หอ', 'กยศ', 'ลงทะเบียน'];

function get(url, name) {
  const res = http.get(url, { tags: { name } });
  if (res.status >= 500) errors5xx.add(1);
  check(res, { [`${name} 2xx`]: (r) => r.status >= 200 && r.status < 300 });
  return res;
}

export default function (data) {
  group('home', () => {
    get(`${BASE}/`, 'GET /');
    get(`${BASE}/api/categories`, 'GET /api/categories');
  });
  sleep(1 + Math.random());

  group('board', () => {
    get(`${BASE}/api/boards/${pick(data.boardIds)}/posts?page=0&size=10`, 'GET /api/boards/:id/posts');
  });
  sleep(1 + Math.random() * 2);

  if (data.postIds.length) {
    group('post', () => {
      const id = pick(data.postIds);
      get(`${BASE}/api/posts/${id}`, 'GET /api/posts/:id');
      get(`${BASE}/api/posts/${id}/comments`, 'GET /api/posts/:id/comments');
    });
    sleep(2 + Math.random() * 3);   // reading time
  }

  if (Math.random() < 0.15) {
    group('search', () => {
      get(`${BASE}/api/posts/search?q=${encodeURIComponent(pick(SEARCH_WORDS))}`, 'GET /api/posts/search');
    });
    sleep(1);
  }
}
