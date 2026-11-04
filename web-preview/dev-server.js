#!/usr/bin/env node
/* ══════════════════════════════════════════════════════════════════════════
   DuelShots · preview dev server
   Zero dependencies. Serves the interactive preview and exposes a small
   /*?lint endpoint that reports unresolved data-action hooks — handy while
   editing, invisible to the app itself.
   ══════════════════════════════════════════════════════════════════════════ */

const http = require('http');
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const ROOT = __dirname;
const PORT = Number(process.env.PORT || 3000);
const HOST = process.env.HOST || '0.0.0.0';

const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
};

/* Static lint: every data-action emitted by app.js should have a handler. */
function lint() {
  const js = fs.readFileSync(path.join(ROOT, 'app.js'), 'utf8');
  const html = fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8');
  const emitted = new Set();
  const actionRe = /data-action="([a-z-]+)(?::|\?|"|\$\{)/g;
  let m;
  const sources = js + '\n' + html;
  while ((m = actionRe.exec(sources))) emitted.add(m[1]);

  const handled = new Set();
  const caseRe = /case '([a-z-]+)':/g;
  while ((m = caseRe.exec(js))) handled.add(m[1]);

  const missing = [...emitted].filter((a) => !handled.has(a) && a !== 'action');
  const unused = [...handled].filter((a) => !emitted.has(a));

  const idRe = /id="([A-Za-z0-9_-]+)"/g;
  const ids = new Set();
  while ((m = idRe.exec(html))) ids.add(m[1]);
  const queried = new Set();
  const qRe = /getElementById\('([A-Za-z0-9_-]+)'\)/g;
  while ((m = qRe.exec(js))) queried.add(m[1]);
  const missingIds = [...queried].filter((id) => !ids.has(id));

  return { ok: missing.length === 0 && missingIds.length === 0, emittedActions: [...emitted].sort(), handledActions: [...handled].sort(), missing, unused, missingIds };
}

function send(res, status, body, headers = {}) {
  res.writeHead(status, {
    'Cache-Control': 'no-cache',
    'Access-Control-Allow-Origin': '*',
    ...headers,
  });
  res.end(body);
}

const server = http.createServer((req, res) => {
  let urlPath;
  try {
    urlPath = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
  } catch {
    return send(res, 400, 'Bad request');
  }

  if (urlPath === '/__lint') {
    const report = lint();
    return send(res, report.ok ? 200 : 500, JSON.stringify(report, null, 2), { 'Content-Type': 'application/json; charset=utf-8' });
  }

  if (urlPath === '/') urlPath = '/index.html';

  /* Resolve inside ROOT only. */
  const filePath = path.join(ROOT, path.normalize(urlPath).replace(/^(\.\.[/\\])+/, ''));
  if (!filePath.startsWith(ROOT)) return send(res, 403, 'Forbidden');

  fs.readFile(filePath, (err, data) => {
    if (err) return send(res, 404, 'Not found');
    const ext = path.extname(filePath).toLowerCase();
    const headers = {
      'Content-Type': TYPES[ext] || 'application/octet-stream',
      /* SVG/HTML previews get embedded, so no frame-ancestors restriction. */
      'X-Content-Type-Options': 'nosniff',
      'Access-Control-Allow-Origin': '*',
    };

    const acceptEncoding = String(req.headers['accept-encoding'] || '');
    if (/gzip/.test(acceptEncoding) && data.length > 1024) {
      zlib.gzip(data, (zipErr, zipped) => {
        if (zipErr) return send(res, 200, data, headers);
        send(res, 200, zipped, { ...headers, 'Content-Encoding': 'gzip', Vary: 'Accept-Encoding' });
      });
    } else {
      send(res, 200, data, headers);
    }
  });
});

server.listen(PORT, HOST, () => {
  console.log(`DuelShots preview → http://${HOST}:${PORT}`);
  const report = lint();
  console.log(report.ok
    ? `lint: ok — ${report.handledActions.length} actions wired`
    : `lint: problems — missing handlers: ${report.missing.join(', ') || 'none'}; missing ids: ${report.missingIds.join(', ') || 'none'}`);
});
