/**
 * Bridge entre Web e Android Native NFC
 * Detecta se está rodando no WebView e usa capacidades nativas
 */

const NativeBridge = {
  isAndroidApp: false,
  
  init() {
    // Detecta se está rodando no Android WebView
    this.isAndroidApp = typeof AndroidNFC !== 'undefined';
    console.log('[Bridge] Running in Android WebView:', this.isAndroidApp);
    
    if (this.isAndroidApp) {
      console.log('[Bridge] Native NFC available');
    }
  },
  
  /**
   * Inicia leitura NFC
   * @param {number} amount - Valor em centavos
   * @param {string} type - 'debit' ou 'credit'
   * @returns {Promise<object>} Dados do cartão
   */
  async readCard(amount, type) {
    if (this.isAndroidApp) {
      return new Promise((resolve, reject) => {
        // Callback global para o Android chamar
        window.onNfcSuccess = (cardDataJson) => {
          try {
            const cardData = JSON.parse(cardDataJson);
            console.log('[Bridge] Card read success:', cardData);
            resolve(cardData);
          } catch (e) {
            reject(new Error('Failed to parse card data'));
          }
        };
        
        window.onNfcError = (errorMsg) => {
          console.error('[Bridge] Card read error:', errorMsg);
          reject(new Error(errorMsg));
        };
        
        // Chama método nativo do Android
        try {
          AndroidNFC.startNfcRead(amount, type);
        } catch (e) {
          reject(new Error('Failed to start NFC read: ' + e.message));
        }
      });
    } else {
      // Fallback: tenta Web NFC API (só funciona com tags NDEF)
      throw new Error('Web NFC API não suporta cartões EMV. Use o app Android.');
    }
  },
  
  /**
   * Cancela leitura NFC em andamento
   */
  cancelRead() {
    if (this.isAndroidApp && typeof AndroidNFC.cancelNfcRead === 'function') {
      AndroidNFC.cancelNfcRead();
    }
  },
  
  /**
   * Vibra o dispositivo
   * @param {number[]} pattern - Padrão de vibração em ms
   */
  vibrate(pattern) {
    if (this.isAndroidApp && typeof AndroidNFC.vibrate === 'function') {
      AndroidNFC.vibrate(JSON.stringify(pattern));
    } else if (navigator.vibrate) {
      navigator.vibrate(pattern);
    }
  }
};

// Inicializa ao carregar
NativeBridge.init();
