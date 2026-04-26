/**
 * TapOpenSource — Main App Controller
 */

const App = (() => {
  // --- State ---
  let amountCents = 0;
  let paymentType = 'debit';

  // --- DOM refs ---
  const screens = {
    amount:     document.getElementById('screen-amount'),
    nfc:        document.getElementById('screen-nfc'),
    processing: document.getElementById('screen-processing'),
    result:     document.getElementById('screen-result'),
  };

  const amountDisplay    = document.getElementById('amount-value');
  const btnCharge        = document.getElementById('btn-charge');
  const nfcStatus        = document.getElementById('nfc-status');
  const processingStatus = document.getElementById('processing-status');

  // --- Vibração utilitária ---
  // Deve ser chamada o mais próximo possível de um gesto do usuário
  const vibrate = (pattern) => {
    try { if (navigator.vibrate) navigator.vibrate(pattern); } catch (_) {}
  };

  // --- Screen navigation ---
  const showScreen = (name) => {
    Object.values(screens).forEach(s => s.classList.remove('active'));
    screens[name].classList.add('active');
  };

  // --- Amount formatting ---
  const formatAmount = (cents) => {
    const value = (cents / 100).toFixed(2);
    const [intPart, decPart] = value.split('.');
    // Adiciona separador de milhar
    const intFormatted = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    return `${intFormatted},${decPart}`;
  };

  const updateAmountDisplay = () => {
    amountDisplay.textContent = `R$ ${formatAmount(amountCents)}`;
    amountDisplay.classList.toggle('has-value', amountCents > 0);
    btnCharge.disabled = amountCents === 0;
  };

  // --- Numpad ---
  document.querySelector('.tap-numpad').addEventListener('click', (e) => {
    const btn = e.target.closest('[data-key]');
    if (!btn) return;
    const key = btn.dataset.key;

    if (key === 'clear') {
      amountCents = 0;
    } else if (key === 'backspace') {
      amountCents = Math.floor(amountCents / 10);
    } else if (!isNaN(key)) {
      if (amountCents > 9999999) return;
      amountCents = amountCents * 10 + parseInt(key);
    }

    updateAmountDisplay();
  });

  // --- Payment type toggle ---
  document.querySelector('.tap-toggle').addEventListener('click', (e) => {
    const btn = e.target.closest('.tap-toggle__btn');
    if (!btn) return;
    paymentType = btn.dataset.type;
    document.querySelectorAll('.tap-toggle__btn').forEach(b => {
      b.classList.remove('active');
      b.setAttribute('aria-pressed', 'false');
    });
    btn.classList.add('active');
    btn.setAttribute('aria-pressed', 'true');
  });

  // --- Charge button ---
  btnCharge.addEventListener('click', () => {
    vibrate(80);
    Log.info('app:charge_initiated', { amount: amountCents, type: paymentType });
    showScreen('nfc');
    startNFCRead();
  });

  // --- Cancel NFC ---
  document.getElementById('btn-cancel-nfc').addEventListener('click', () => {
    NFC.stopScan();
    showScreen('amount');
  });

  // --- New charge ---
  document.getElementById('btn-new-charge').addEventListener('click', () => {
    amountCents = 0;
    updateAmountDisplay();
    showScreen('amount');
  });

  // --- NFC Flow ---
  const startNFCRead = () => {
    nfcStatus.textContent = 'Aguardando leitura NFC...';

    if (!NFC.isSupported()) {
      showResult(false, { message: 'NFC não suportado neste dispositivo.' });
      return;
    }

    NFC.startScan(
      (nfcData) => {
        vibrate([60, 40, 60]);
        NFC.stopScan();
        nfcStatus.textContent = 'Cartão detectado!';
        Log.info('app:nfc_read_success', { source: NFC.extractCardToken(nfcData).source });
        const cardInfo = NFC.extractCardToken(nfcData);
        processPayment(cardInfo);
      },
      (err) => {
        vibrate(300);
        NFC.stopScan();
        nfcStatus.textContent = err.message;
        Log.error('app:nfc_read_error', { message: err.message });
        showResult(false, { message: err.message });
      },
      // Passa amount e type para o Android nativo
      { amount: amountCents, type: paymentType }
    );
  };

  // --- Payment processing ---
  const processPayment = async (cardInfo) => {
    showScreen('processing');
    processingStatus.textContent = 'Enviando ao gateway...';

    const result = await Gateway.charge({
      amount: amountCents,
      type: paymentType,
      cardToken: cardInfo.token,
      source: cardInfo.source,
      brand: cardInfo.brand,
      expiry: cardInfo.expiry,
      holderName: cardInfo.holderName,
    });

    // Vibra conforme resultado
    if (result.success) {
      vibrate([100, 50, 100, 50, 200]); // padrão aprovado
    } else {
      vibrate([200, 100, 200]);          // padrão recusado
    }

    showResult(result.success, {
      message:       result.message,
      transactionId: result.transactionId,
      authCode:      result.authCode,
      amount:        amountCents,
      type:          paymentType,
    });
  };

  // --- Result screen ---
  const CHECK_SVG = `<svg xmlns="http://www.w3.org/2000/svg" width="36" height="36" fill="currentColor" viewBox="0 0 16 16">
    <path d="M13.854 3.646a.5.5 0 0 1 0 .708l-7 7a.5.5 0 0 1-.708 0l-3.5-3.5a.5.5 0 1 1 .708-.708L6.5 10.293l6.646-6.647a.5.5 0 0 1 .708 0z"/>
  </svg>`;

  const X_SVG = `<svg xmlns="http://www.w3.org/2000/svg" width="36" height="36" fill="currentColor" viewBox="0 0 16 16">
    <path d="M4.646 4.646a.5.5 0 0 1 .708 0L8 7.293l2.646-2.647a.5.5 0 0 1 .708.708L8.707 8l2.647 2.646a.5.5 0 0 1-.708.708L8 8.707l-2.646 2.647a.5.5 0 0 1-.708-.708L7.293 8 4.646 5.354a.5.5 0 0 1 0-.708z"/>
  </svg>`;

  const showResult = (success, { message, transactionId, authCode, amount, type }) => {
    const iconEl = document.getElementById('result-icon');
    iconEl.className = `tap-result-icon tap-result-icon--${success ? 'success' : 'error'}`;
    iconEl.innerHTML = success ? CHECK_SVG : X_SVG;

    document.getElementById('result-title').textContent   = success ? 'Aprovado!' : 'Recusado';
    document.getElementById('result-message').textContent = message;

    const details = document.getElementById('result-details');

    if (success && transactionId) {
      details.innerHTML = `
        <div class="tap-result-row"><span class="tap-result-row__label">Valor</span><span class="tap-result-row__value">R$ ${formatAmount(amount)}</span></div>
        <div class="tap-result-row"><span class="tap-result-row__label">Tipo</span><span class="tap-result-row__value">${type === 'debit' ? 'Débito' : 'Crédito'}</span></div>
        <div class="tap-result-row"><span class="tap-result-row__label">Autorização</span><span class="tap-result-row__value">${authCode}</span></div>
        <div class="tap-result-row"><span class="tap-result-row__label">ID</span><span class="tap-result-row__value">${transactionId}</span></div>
      `;
      details.style.display = '';
    } else {
      details.innerHTML = '';
      details.style.display = 'none';
    }

    showScreen('result');
  };

  // --- Init ---
  updateAmountDisplay();

})();
