/* BoardKub — shared frontend helpers */

// Nginx proxies /api to the backend Service, so a relative base works everywhere.
const API_BASE = window.API_BASE_URL || '/api';

/* ---------------- API client ---------------- */

async function api(path, options = {}) {
  const isForm = options.body instanceof FormData;
  const opts = {
    credentials: 'include', // send the KMITLSESSION cookie
    // For FormData the browser sets multipart/form-data with its boundary itself
    headers: isForm ? {} : { 'Content-Type': 'application/json' },
    ...options,
  };
  if (opts.body && !isForm && typeof opts.body !== 'string') {
    opts.body = JSON.stringify(opts.body);
  }

  let res;
  try {
    res = await fetch(API_BASE + path, opts);
  } catch {
    throw new Error('เชื่อมต่อเซิร์ฟเวอร์ไม่ได้ กรุณาลองใหม่อีกครั้ง');
  }

  const text = await res.text();
  let data = null;
  if (text) {
    // Proxy error pages (502/503/504) are HTML, not JSON: never show them raw.
    try { data = JSON.parse(text); } catch { data = null; }
  }

  if (!res.ok) {
    let fallback = 'เกิดข้อผิดพลาด (' + res.status + ')';
    if (res.status === 502 || res.status === 503 || res.status === 504) {
      fallback = 'ระบบหลังบ้านยังไม่พร้อมให้บริการ กรุณารอสักครู่แล้วลองใหม่';
    } else if (res.status === 413) {
      fallback = 'ไฟล์มีขนาดใหญ่เกินไป (ไม่เกิน 5 MB)';
    } else if (res.status === 429) {
      fallback = 'มีการส่งคำขอถี่เกินไป กรุณารอสักครู่แล้วลองใหม่';
    }
    const err = new Error((data && data.message) || fallback);
    err.status = res.status;
    throw err;
  }
  return data;
}

const API = {
  me: () => api('/auth/me'),
  whoami: () => api('/auth/whoami'),
  login: (body) => api('/auth/login', { method: 'POST', body }),
  logout: () => api('/auth/logout', { method: 'POST' }),
  register: (body) => api('/auth/register', { method: 'POST', body }),
  verify: (body) => api('/auth/verify', { method: 'POST', body }),
  resendOtp: (body) => api('/auth/resend-otp', { method: 'POST', body }),

  categories: () => api('/categories'),
  boards: (categoryId) => api('/boards' + (categoryId ? '?categoryId=' + categoryId : '')),
  board: (id) => api('/boards/' + id),

  posts: (boardId, page, size, sort) =>
    api('/boards/' + boardId + '/posts?page=' + page + '&size=' + size + '&sort=' + sort),
  popular: () => api('/posts/popular'),
  search: (q, page, size) =>
    api('/posts/search?q=' + encodeURIComponent(q) + '&page=' + page + '&size=' + size),
  post: (id) => api('/posts/' + id),
  createPost: (boardId, body) => api('/boards/' + boardId + '/posts', { method: 'POST', body }),
  updatePost: (id, body) => api('/posts/' + id, { method: 'PUT', body }),
  deletePost: (id) => api('/posts/' + id, { method: 'DELETE' }),
  uploadImage: (postId, blob, name) => {
    const fd = new FormData();
    fd.append('file', blob, name);
    return api('/posts/' + postId + '/images', { method: 'POST', body: fd });
  },
  deleteImage: (id) => api('/images/' + id, { method: 'DELETE' }),

  comments: (postId) => api('/posts/' + postId + '/comments'),
  addComment: (postId, body) => api('/posts/' + postId + '/comments', { method: 'POST', body }),
  updateComment: (id, body) => api('/comments/' + id, { method: 'PUT', body }),
  deleteComment: (id) => api('/comments/' + id, { method: 'DELETE' }),

  createCategory: (body) => api('/admin/categories', { method: 'POST', body }),
  deleteCategory: (id) => api('/admin/categories/' + id, { method: 'DELETE' }),
  createBoard: (body) => api('/admin/boards', { method: 'POST', body }),
  deleteBoard: (id) => api('/admin/boards/' + id, { method: 'DELETE' }),
};

/* ---------------- Helpers ---------------- */

const MAX_IMAGES_PER_POST = 4;
const MAX_IMAGE_SIDE = 1600;

/**
 * Shrinks a photo in the browser before upload: the longest side becomes at most
 * 1600px and the file is re-encoded, which applies the camera's rotation and drops
 * EXIF data (GPS location). The server checks and re-encodes again anyway.
 * Returns { blob, name, url } where url is a preview object URL.
 */
async function shrinkImage(file) {
  if (!/^image\/(jpeg|png)$/.test(file.type)) {
    throw new Error('รองรับเฉพาะรูป JPG และ PNG (' + file.name + ')');
  }
  let bmp;
  try {
    bmp = await createImageBitmap(file, { imageOrientation: 'from-image' });
  } catch {
    throw new Error('เปิดรูป ' + file.name + ' ไม่ได้');
  }
  const scale = Math.min(1, MAX_IMAGE_SIDE / Math.max(bmp.width, bmp.height));
  const w = Math.max(1, Math.round(bmp.width * scale));
  const h = Math.max(1, Math.round(bmp.height * scale));
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext('2d');
  const asPng = file.type === 'image/png';
  if (!asPng) { ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, w, h); }
  ctx.drawImage(bmp, 0, 0, w, h);
  if (bmp.close) bmp.close();
  const blob = await new Promise(function (resolve) {
    canvas.toBlob(resolve, asPng ? 'image/png' : 'image/jpeg', 0.85);
  });
  if (!blob) throw new Error('ย่อรูป ' + file.name + ' ไม่สำเร็จ');
  if (blob.size > 5 * 1024 * 1024) throw new Error('รูป ' + file.name + ' ยังใหญ่เกิน 5 MB หลังย่อ');
  return { blob: blob, name: asPng ? 'image.png' : 'image.jpg', url: URL.createObjectURL(blob) };
}

/** Only URLs we produce (Object Storage https or local /api/media/) go into <img src>. */
function safeImageUrl(url) {
  return /^(https:\/\/|\/api\/media\/)/.test(String(url || '')) ? url : '';
}

/** Escapes text before it goes into the DOM (XSS protection). */
function esc(str) {
  if (str === null || str === undefined) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function qs(name) {
  return new URLSearchParams(window.location.search).get(name);
}

function timeAgo(iso) {
  if (!iso) return '';
  const then = new Date(iso);
  const diff = (Date.now() - then.getTime()) / 1000;
  if (diff < 60) return 'เมื่อสักครู่';
  if (diff < 3600) return Math.floor(diff / 60) + ' นาทีที่แล้ว';
  if (diff < 86400) return Math.floor(diff / 3600) + ' ชั่วโมงที่แล้ว';
  if (diff < 604800) return Math.floor(diff / 86400) + ' วันที่แล้ว';
  return then.toLocaleDateString('th-TH', { year: 'numeric', month: 'short', day: 'numeric' });
}

function showAlert(id, message, type = 'error') {
  const el = document.getElementById(id);
  if (!el) return;
  el.textContent = message;
  el.className = 'alert show ' + type;
}

function hideAlert(id) {
  const el = document.getElementById(id);
  if (el) el.className = 'alert';
}

/* ---------------- Session state ---------------- */

let CURRENT_USER = null;

async function loadUser() {
  try {
    CURRENT_USER = await API.me();
  } catch {
    CURRENT_USER = null;
  }
  return CURRENT_USER;
}

function requireLogin() {
  if (!CURRENT_USER) {
    location.href = 'login.html?next=' + encodeURIComponent(location.pathname + location.search);
    return false;
  }
  return true;
}

/* ---------------- Shared chrome ---------------- */

function renderTopbar(activeSearch = '') {
  const nav = document.getElementById('nav-area');
  if (nav) {
    if (CURRENT_USER) {
      const adminLink = CURRENT_USER.role === 'ADMIN'
        ? '<a class="btn ghost sm" href="admin.html">จัดการระบบ</a>' : '';
      nav.innerHTML = adminLink +
        '<span class="who">' + esc(CURRENT_USER.displayName) + '</span>' +
        '<a class="btn ghost sm" href="#" id="logout-link">ออกจากระบบ</a>';
      const lo = document.getElementById('logout-link');
      if (lo) {
        lo.addEventListener('click', async (e) => {
          e.preventDefault();
          await API.logout();
          location.href = 'index.html';
        });
      }
    } else {
      nav.innerHTML =
        '<a class="btn ghost sm" href="login.html">เข้าสู่ระบบ</a>' +
        '<a class="btn sm" href="register.html">สมัครสมาชิก</a>';
    }
  }

  const search = document.getElementById('search-input');
  if (search) {
    search.value = activeSearch;
    search.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && search.value.trim()) {
        location.href = 'search.html?q=' + encodeURIComponent(search.value.trim());
      }
    });
  }
}

/** Shows which Pod answered, so multi-Pod scaling can be demonstrated. */
async function renderPodBar() {
  const el = document.getElementById('podbar');
  if (!el) return;
  try {
    const info = await API.whoami();
    el.innerHTML = 'Pod <code>' + esc(info.servedByPod) + '</code>'
      + (info.loggedIn ? ' · Session อ่านจาก Redis' : '');
  } catch {
    el.textContent = '';
  }
}

/**
 * Fills the category sidebar. Each category links to its own page;
 * activeCatId highlights the one being viewed: 'popular' (default view of
 * categories.html), 'home' (categories.html?view=all) or a category id.
 */
async function renderSidebar(activeCatId) {
  const el = document.getElementById('side-cats');
  if (!el) return;
  try {
    const cats = await API.categories();
    const link = (key, href, label) => '<a class="side-link' + (activeCatId === key ? ' active' : '') +
      '" href="' + href + '"' + (activeCatId === key ? ' aria-current="page"' : '') +
      '><span class="n">' + label + '</span></a>';
    const top = link('popular', 'categories.html', 'กระทู้ยอดนิยม') +
      link('home', 'categories.html?view=all', 'ทุกหมวดหมู่');
    el.innerHTML = top + cats.map((c) =>
      '<a class="side-link' + (String(c.id) === String(activeCatId) ? ' active' : '') +
      '" href="category.html?id=' + c.id + '"' +
      (String(c.id) === String(activeCatId) ? ' aria-current="page"' : '') + '>' +
      '<span class="n">' + esc(c.name) + '</span>' +
      '<span class="c">' + c.postCount + '</span></a>'
    ).join('');
  } catch {
    el.innerHTML = '';
  }
}

async function initPage(activeSearch = '', activeCatId = null) {
  await loadUser();
  renderTopbar(activeSearch);
  renderPodBar();
  renderSidebar(activeCatId);
}
