/**
 * TapOpenSource — Logs Worker (Cloudflare Workers + KV)
 *
 * POST /log   — recebe evento do frontend
 * GET  /logs  — lista os últimos 100 logs (protegido por token)
 * DELETE /logs — limpa todos os logs
 */

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, DELETE, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, X-Log-Token',
};

export default {
  async fetch(request, env) {
    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: CORS });
    }

    const url = new URL(request.url);

    // POST /log — ingere um evento
    if (request.method === 'POST' && url.pathname === '/log') {
      return handleIngest(request, env);
    }

    // GET /logs — lista eventos
    if (request.method === 'GET' && url.pathname === '/logs') {
      return handleList(request, env);
    }

    // DELETE /logs — limpa
    if (request.method === 'DELETE' && url.pathname === '/logs') {
      return handleClear(request, env);
    }

    return new Response('TapOpenSource Logs Worker', { status: 200, headers: CORS });
  },
};

// ── Ingest ────────────────────────────────────────────────
async function handleIngest(request, env) {
  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: 'invalid json' }, 400);
  }

  const entry = {
    id:        crypto.randomUUID(),
    ts:        new Date().toISOString(),
    level:     body.level || 'info',   // info | warn | error
    event:     body.event || 'unknown',
    data:      body.data || {},
    ua:        request.headers.get('user-agent')?.slice(0, 120) || '',
    ip:        request.headers.get('cf-connecting-ip') || '',
    country:   request.cf?.country || '',
  };

  // Salva no KV com TTL de 7 dias
  await env.LOGS.put(`log:${entry.ts}:${entry.id}`, JSON.stringify(entry), {
    expirationTtl: 60 * 60 * 24 * 7,
  });

  console.log(`[${entry.level.toUpperCase()}] ${entry.event}`, JSON.stringify(entry.data));

  return json({ ok: true, id: entry.id }, 201);
}

// ── List ──────────────────────────────────────────────────
async function handleList(request, env) {
  // Proteção simples por query token: /logs?token=SEU_TOKEN
  const url = new URL(request.url);
  const token = url.searchParams.get('token');
  if (token !== env.LOG_TOKEN) {
    return json({ error: 'unauthorized' }, 401);
  }

  const list = await env.LOGS.list({ prefix: 'log:', limit: 100 });

  const entries = await Promise.all(
    list.keys.map(async ({ name }) => {
      const val = await env.LOGS.get(name);
      try { return JSON.parse(val); } catch { return null; }
    })
  );

  const logs = entries
    .filter(Boolean)
    .sort((a, b) => b.ts.localeCompare(a.ts)); // mais recente primeiro

  return json({ count: logs.length, logs }, 200);
}

// ── Clear ─────────────────────────────────────────────────
async function handleClear(request, env) {
  const url = new URL(request.url);
  if (url.searchParams.get('token') !== env.LOG_TOKEN) {
    return json({ error: 'unauthorized' }, 401);
  }

  const list = await env.LOGS.list({ prefix: 'log:' });
  await Promise.all(list.keys.map(({ name }) => env.LOGS.delete(name)));

  return json({ ok: true, deleted: list.keys.length }, 200);
}

// ── Helper ────────────────────────────────────────────────
const json = (data, status = 200) =>
  new Response(JSON.stringify(data, null, 2), {
    status,
    headers: { ...CORS, 'Content-Type': 'application/json' },
  });
