# 🌉 Web-to-Native Bridge

## Como funciona

O TapOpenSource agora permite que a **web app processe cartões EMV** usando as capacidades nativas do Android através de um **JavaScript Bridge**.

### Arquitetura

```
┌─────────────────────────────────────────┐
│         Web App (HTML/JS/CSS)           │
│  https://tapopensource.pages.dev        │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  bridge.js                      │   │
│  │  Detecta AndroidNFC interface   │   │
│  └─────────────────────────────────┘   │
│              ↓                          │
│  ┌─────────────────────────────────┐   │
│  │  nfc.js                         │   │
│  │  Usa native ou Web NFC API      │   │
│  └─────────────────────────────────┘   │
└─────────────────────────────────────────┘
              ↕ JavaScript Interface
┌─────────────────────────────────────────┐
│    Android WebView (WebViewActivity)    │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  AndroidNFC Interface           │   │
│  │  - startNfcRead()               │   │
│  │  - cancelNfcRead()              │   │
│  │  - vibrate()                    │   │
│  └─────────────────────────────────┘   │
│              ↓                          │
│  ┌─────────────────────────────────┐   │
│  │  EmvReader.kt                   │   │
│  │  Lê cartão via IsoDep           │   │
│  └─────────────────────────────────┘   │
└─────────────────────────────────────────┘
```

## Componentes

### 1. bridge.js (Web)

Detecta se está rodando no Android WebView e expõe API unificada:

```javascript
const NativeBridge = {
  isAndroidApp: typeof AndroidNFC !== 'undefined',
  
  async readCard(amount, type) {
    if (this.isAndroidApp) {
      // Usa NFC nativo do Android
      return new Promise((resolve, reject) => {
        window.onNfcSuccess = (cardDataJson) => resolve(JSON.parse(cardDataJson));
        window.onNfcError = (errorMsg) => reject(new Error(errorMsg));
        AndroidNFC.startNfcRead(amount, type);
      });
    } else {
      // Fallback: Web NFC API (só tags NDEF)
      throw new Error('Web NFC não suporta cartões EMV');
    }
  }
};
```

### 2. WebViewActivity.kt (Android)

Carrega a web app e expõe interface JavaScript:

```kotlin
inner class AndroidNFCInterface {
    @JavascriptInterface
    fun startNfcRead(amount: Int, type: String) {
        pendingNfcRead = PendingNfcRead(amount, type)
    }
    
    @JavascriptInterface
    fun cancelNfcRead() {
        pendingNfcRead = null
    }
    
    @JavascriptInterface
    fun vibrate(patternJson: String) {
        // Vibra o dispositivo
    }
}
```

### 3. nfc.js (Web)

Atualizado para usar bridge quando disponível:

```javascript
const startScan = async (onRead, onError, options = {}) => {
  // Prioriza Android nativo
  if (typeof AndroidNFC !== 'undefined') {
    const cardData = await NativeBridge.readCard(options.amount, options.type);
    onRead(cardData);
    return;
  }
  
  // Fallback: Web NFC API
  // ...
};
```

## Como usar

### No Android App

1. Abra o app TapOpenSource
2. Toque em **"🌐 Modo Web (com NFC nativo)"**
3. A web app carrega dentro do WebView
4. Digite o valor e aproxime o cartão
5. O NFC nativo do Android lê o cartão EMV
6. Os dados retornam para a web app via JavaScript

### Fluxo de dados

1. **Web**: Usuário clica em "Cobrar"
2. **Web**: `NativeBridge.readCard(1000, 'debit')` é chamado
3. **Android**: `AndroidNFC.startNfcRead(1000, "debit")` recebe a chamada
4. **Android**: Aguarda tag NFC via `onNewIntent()`
5. **Android**: `EmvReader` lê o cartão via IsoDep
6. **Android**: Converte dados para JSON
7. **Android**: Chama `window.onNfcSuccess(json)` no JavaScript
8. **Web**: Promise resolve com dados do cartão
9. **Web**: Processa pagamento normalmente

## Vantagens

✅ **Web app processa cartões EMV** (antes só funcionava no app nativo)  
✅ **Mesma UI/UX** da web, com poder do Android  
✅ **Sem duplicação de código** - uma única web app  
✅ **Fácil atualização** - deploy na web, app usa automaticamente  
✅ **Fallback automático** - detecta ambiente e usa melhor opção  

## Limitações

⚠️ **Web NFC API** ainda não suporta cartões EMV (só tags NDEF)  
⚠️ **Requer Android app** para processar cartões reais  
⚠️ **WebView** adiciona overhead mínimo vs app nativo puro  

## Segurança

🔒 **JavaScript Interface** expõe apenas métodos necessários  
🔒 **Dados sensíveis** (PAN) são mascarados no log  
🔒 **HTTPS obrigatório** para web app  
🔒 **Network Security Config** valida certificados  

## Desenvolvimento

### Testar localmente

1. Rode um servidor local:
```bash
python -m http.server 8000
```

2. Atualize `WebViewActivity.kt`:
```kotlin
val webAppUrl = "http://10.0.2.2:8000" // Android emulator
// ou
val webAppUrl = "http://192.168.1.100:8000" // Dispositivo físico
```

3. Build e instale o APK

### Debug

Ative o debug do WebView no Chrome:

1. No Android: Ative "Depuração USB" nas Opções do desenvolvedor
2. No Chrome desktop: Abra `chrome://inspect`
3. Conecte o dispositivo via USB
4. Inspecione o WebView

## Roadmap

- [ ] Cache offline da web app (Service Worker)
- [ ] Sincronização bidirecional de estado
- [ ] Suporte para múltiplas web apps
- [ ] Deep linking entre native e web
- [ ] Compartilhamento de sessão/auth

---

**Feito com ⚡ por TapOpenSource**
