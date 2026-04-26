/**
 * TapOpenSource — NFC Reader Module
 * Uses Native Android NFC (via bridge) or Web NFC API fallback
 * https://developer.mozilla.org/en-US/docs/Web/API/Web_NFC_API
 */

const NFC = (() => {
  let reader = null;
  let abortController = null;

  const isSupported = () => {
    // Prioriza Android nativo, fallback para Web NFC
    return (typeof AndroidNFC !== 'undefined') || ('NDEFReader' in window);
  };

  /**
   * Vibração segura — funciona mesmo fora de user gesture.
   * Agenda via setTimeout(0) para sair do call stack atual,
   * o que satisfaz a política do Chrome Android.
   */
  const _vibrate = (pattern) => {
    setTimeout(() => {
      try { if (navigator.vibrate) navigator.vibrate(pattern); } catch (_) {}
    }, 0);
  };

  /**
   * Converte qualquer string (incluindo bytes binários) para base64 seguro.
   * Evita o InvalidCharacterError do btoa() com chars > 255.
   */
  const _safeBase64 = (str) => {
    try {
      // Tenta btoa direto (funciona para seriais ASCII/hex)
      return btoa(str).replace(/=/g, '');
    } catch (_) {
      // Fallback: encode via TextEncoder → Uint8Array → base64
      const bytes = new TextEncoder().encode(str);
      let binary = '';
      bytes.forEach(b => binary += String.fromCharCode(b));
      return btoa(binary).replace(/=/g, '');
    }
  };

  /**
   * Start NFC scan session.
   * @param {function} onRead  - Callback com { serialNumber, records, raw } ou cardData do Android
   * @param {function} onError - Callback com Error
   * @param {object} options - { amount, type } para Android native
   */
  const startScan = async (onRead, onError, options = {}) => {
    // Se está no Android WebView, usa NFC nativo
    if (typeof AndroidNFC !== 'undefined') {
      try {
        _vibrate(80); // vibra ao iniciar
        
        Log.info('nfc:scan_started_native', { amount: options.amount, type: options.type });
        
        const cardData = await NativeBridge.readCard(options.amount || 0, options.type || 'debit');
        
        _vibrate([60, 40, 60]); // sucesso
        
        Log.info('nfc:reading_native', { 
          pan: cardData.pan?.slice(0, 6) + '...', 
          brand: cardData.brand 
        });
        
        onRead(cardData);
        
      } catch (err) {
        _vibrate(300); // erro
        Log.error('nfc:scan_error_native', { message: err.message });
        onError(err);
      }
      return;
    }

    // Fallback: Web NFC API (só funciona com tags NDEF)
    if (!isSupported()) {
      onError(new Error('Web NFC não suportado. Use Chrome no Android com NFC ativado.'));
      return;
    }

    try {
      reader = new NDEFReader();
      abortController = new AbortController();

      await reader.scan({ signal: abortController.signal });

      Log.info('nfc:scan_started');

      reader.addEventListener('reading', ({ serialNumber, message }) => {
        _vibrate([60, 40, 60]); // 2x curto — leitura OK

        const records = message.records.map(record => {
          let data = null;
          try {
            if (record.data) data = new TextDecoder().decode(record.data);
          } catch (_) {}
          return { recordType: record.recordType, mediaType: record.mediaType, data };
        });

        Log.info('nfc:reading', { serialNumber: serialNumber?.slice(0, 8) + '...', recordCount: records.length });

        onRead({ serialNumber, records });
      });

      reader.addEventListener('readingerror', () => {
        _vibrate(300); // longo — falha
        Log.error('nfc:readingerror');
        onError(new Error('Não foi possível ler o cartão. Aproxime novamente devagar.'));
      });

    } catch (err) {
      if (err.name === 'AbortError') return;

      const msg = err.name === 'NotAllowedError'
        ? 'Permissão NFC negada. Habilite nas configurações do Chrome.'
        : `Erro NFC: ${err.message}`;

      Log.error('nfc:scan_error', { name: err.name, message: err.message });
      onError(new Error(msg));
    }
  };

  const stopScan = () => {
    // Cancela no Android nativo
    if (typeof AndroidNFC !== 'undefined') {
      NativeBridge.cancelRead();
      return;
    }
    
    // Cancela Web NFC
    if (abortController) {
      abortController.abort();
      abortController = null;
    }
    reader = null;
  };

  /**
   * Extrai token do cartão a partir dos dados NFC.
   * Usa _safeBase64 para evitar crash com UIDs binários.
   */
  const extractCardToken = (nfcData) => {
    // Se veio do Android nativo, já tem os dados estruturados
    if (nfcData.pan && nfcData.brand) {
      return {
        token: nfcData.pan,
        brand: nfcData.brand,
        expiry: nfcData.expiry,
        holderName: nfcData.holderName,
        source: 'native_emv',
      };
    }
    
    // Fallback: Web NFC (tags NDEF)
    const textRecord = nfcData.records?.find(r => r.recordType === 'text' && r.data);
    const raw = textRecord?.data || nfcData.serialNumber || 'unknown';
    return {
      token: _safeBase64(raw),
      source: textRecord ? 'ndef' : 'serial',
    };
  };

  return { isSupported, startScan, stopScan, extractCardToken };
})();
