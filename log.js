/**
 * TapOpenSource — Log Client
 * Envia eventos para o Cloudflare Worker de logs.
 * Em caso de falha de rede, silencia — nunca bloqueia o fluxo principal.
 */

const Log = (() => {
  const ENDPOINT = 'https://tapopensource-logs.natanaelrodriguesfernandes521.workers.dev/log';

  const send = (level, event, data = {}) => {
    // Fire-and-forget — não bloqueia, não lança exceção
    fetch(ENDPOINT, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ level, event, data }),
      keepalive: true, // garante envio mesmo se a página fechar
    }).catch(() => {}); // silencia erros de rede
  };

  return {
    info:  (event, data) => send('info',  event, data),
    warn:  (event, data) => send('warn',  event, data),
    error: (event, data) => send('error', event, data),
  };
})();
