# ⚡ TapOpenSource

**Transforme seu celular Android em uma maquininha de cartão virtual via NFC**

TapOpenSource é um projeto open source que permite processar pagamentos por aproximação (contactless) usando apenas o NFC do seu smartphone Android. Leia cartões EMV (Visa, Mastercard, Maestro, Elo) e processe transações de débito e crédito.

---

## 🚀 Features

- ✅ **Leitura EMV completa** via `IsoDep` (Android NFC)
- ✅ **Multi-bandeira**: Visa, Mastercard, Maestro, Elo
- ✅ **PDOL dinâmico** com valores realistas para compatibilidade
- ✅ **Parser TLV robusto** (BER-TLV completo)
- ✅ **Web App** com NFC Web API (tags NDEF)
- ✅ **Web-to-Native Bridge** - Web app processa cartões EMV via Android
- ✅ **Design system próprio** (tap.css)
- ✅ **Logs centralizados** via Cloudflare Worker
- ✅ **Gateway plugável** (mock incluído)
- ✅ **Vibração** em cada etapa da transação
- ✅ **Animação NFC** com 3 anéis pulsantes

---

## 📱 Screenshots

### Android App
- Tela de valor com numpad
- Seleção débito/crédito
- Animação NFC ao aproximar cartão
- Tela de resultado (aprovado/recusado)

### Web App
- Interface responsiva
- Mesmo design system do Android
- Fontes self-hosted (Inter + JetBrains Mono)

---

## 🛠️ Stack Tecnológica

### Android (Kotlin)
- **NFC**: `android.nfc.tech.IsoDep`
- **UI**: Material Design 3, ViewBinding
- **Arquitetura**: MVVM com StateFlow
- **Fonte**: Inter (UI) + JetBrains Mono (monospace)
- **Build**: Gradle 8.x

### Web (Vanilla JS)
- **NFC**: Web NFC API (`NDEFReader`)
- **CSS**: tap.css (design system próprio)
- **Deploy**: Cloudflare Pages
- **Fontes**: Self-hosted (woff2)

### Backend
- **Logs**: Cloudflare Worker + KV
- **Gateway**: Mock (plugável para Cielo, Stone, Stripe, etc.)

---

## 📦 Instalação

### Android

1. **Clone o repositório:**
```bash
git clone https://github.com/pronatan/TapOpenSource.git
cd TapOpenSource
```

2. **Build via Gradle:**
```bash
cd android
./gradlew assembleDebug
```

3. **Instale o APK:**
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Ou baixe o APK pré-compilado: [`TapOpenSource.apk`](TapOpenSource.apk)

### Web

1. **Instale dependências:**
```bash
npm install -g wrangler
```

2. **Deploy para Cloudflare Pages:**
```bash
wrangler pages deploy . --project-name=tapopensource
```

3. **Acesse:**
```
https://tapopensource.pages.dev
```

---

## 🔧 Configuração

### Gateway de Pagamento

Edite `android/app/src/main/java/dev/tapopensource/app/gateway/GatewayClient.kt`:

```kotlin
private val ENDPOINT: String? = "https://api.seu-gateway.com/charge"
private const val API_KEY = "sua_api_key"
```

Suporta qualquer gateway que aceite:
- `amount` (centavos)
- `currency` (BRL)
- `payment_method` (debit/credit)
- `card.token` (dados EMV)

### Logs (Cloudflare Worker)

Deploy do worker de logs:

```bash
cd worker-logs
wrangler deploy
```

Configure o token em `worker-logs/wrangler.toml`:
```toml
[vars]
LOG_TOKEN = "seu_token_secreto"
```

---

## 🌉 Web-to-Native Bridge

A web app agora pode **processar cartões EMV** usando as capacidades nativas do Android!

### Como funciona

1. Abra o app Android TapOpenSource
2. Toque em **"🌐 Modo Web (com NFC nativo)"**
3. A web app carrega em um WebView
4. Digite o valor e aproxime o cartão
5. O NFC nativo lê o cartão EMV
6. Os dados retornam para a web via JavaScript

### Arquitetura

```
Web App (JS) ←→ JavaScript Interface ←→ Android Native (Kotlin)
   bridge.js         WebViewActivity         EmvReader.kt
```

**Vantagens:**
- ✅ Web app processa cartões EMV (antes só tags NDEF)
- ✅ Mesma UI/UX da web com poder do Android
- ✅ Fácil atualização - deploy na web, app usa automaticamente

📖 **Documentação completa:** [BRIDGE.md](BRIDGE.md)

---

## 📖 Como Funciona

### Fluxo EMV (Android)

1. **SELECT PPSE** - Descobre aplicações disponíveis no cartão
2. **Extrai AIDs** - Identifica bandeiras (Visa, Mastercard, etc.)
3. **SELECT AID** - Seleciona a aplicação
4. **GET PROCESSING OPTIONS (GPO)** - Obtém configurações do cartão
5. **READ RECORD** - Lê dados do cartão (PAN, expiry, nome)
6. **Processa pagamento** - Envia ao gateway

### Parser TLV

O projeto inclui um parser BER-TLV completo que suporta:
- Tags de 1 e 2 bytes
- Comprimentos BER (0x81, 0x82)
- Templates aninhados (6F → A5 → BF0C → 61 → 4F)
- Busca recursiva de AIDs

### PDOL (Processing Data Object List)

Valores configurados para compatibilidade máxima:
- **9F66**: Terminal Transaction Qualifiers (Contactless EMV)
- **9F1A**: Country Code (0x0076 = Brasil)
- **5F2A**: Currency Code (0x0986 = BRL)
- **9A**: Transaction Date (formato BCD)
- **9F37**: Unpredictable Number (SecureRandom)

---

## 🎨 Design System (tap.css)

### Cores
```css
--tap-bg:       #f0fdf4;  /* Verde claro */
--tap-primary:  #16a34a;  /* Verde */
--tap-border:   #bbf7d0;  /* Verde claro */
--tap-text:     #111827;  /* Preto */
--tap-muted:    #6b7280;  /* Cinza */
```

### Componentes
- `.tap-card` - Cartões com borda verde
- `.tap-btn--primary` - Botão verde
- `.tap-numpad` - Grid 3x4 do teclado
- `.tap-toggle` - Seletor débito/crédito
- `.tap-nfc-wrap` - Animação de 3 anéis pulsantes

---

## 🧪 Testes

### Testar com cartão real

1. Ative NFC no Android
2. Abra o app TapOpenSource
3. Digite um valor (ex: R$ 10,00)
4. Selecione débito ou crédito
5. Toque em "Cobrar"
6. Aproxime o cartão

### Logs de debug

Acesse os logs em tempo real:
```
https://seu-worker.workers.dev/logs?token=seu_token
```

---

## 🤝 Contribuindo

Contribuições são bem-vindas! Para contribuir:

1. Fork o projeto
2. Crie uma branch (`git checkout -b feature/nova-feature`)
3. Commit suas mudanças (`git commit -m 'Add nova feature'`)
4. Push para a branch (`git push origin feature/nova-feature`)
5. Abra um Pull Request

### Áreas para contribuir

- [ ] Suporte para mais bandeiras (Amex, Diners, Hipercard)
- [ ] Extração completa de PAN (atualmente mascarado)
- [ ] Verificação de PIN
- [ ] Integração com gateways reais (Cielo, Stone, Stripe)
- [ ] Modo offline (armazenamento local)
- [ ] Relatórios e dashboard
- [ ] Suporte iOS (CoreNFC)

---

## 📄 Licença

MIT License - veja [LICENSE](LICENSE) para detalhes.

---

## ⚠️ Avisos Importantes

### Segurança
- Este é um projeto educacional/demonstrativo
- **NÃO use em produção sem certificação PCI-DSS**
- Dados de cartão são sensíveis - implemente criptografia adequada
- Logs podem conter informações sensíveis - proteja adequadamente

### Compatibilidade
- **Android**: Requer NFC e Android 5.0+ (API 21+)
- **Web**: Requer Chrome Android com NFC (limitado a tags NDEF)
- **Cartões**: Apenas cartões EMV contactless

### Limitações
- Web NFC API não lê cartões EMV (apenas tags NDEF)
- Alguns cartões podem rejeitar GPO por falta de certificação
- PAN completo pode não ser extraído (depende do cartão)

---

## 📞 Suporte

- **Issues**: [GitHub Issues](https://github.com/pronatan/TapOpenSource/issues)
- **Discussões**: [GitHub Discussions](https://github.com/pronatan/TapOpenSource/discussions)

---

## 🙏 Agradecimentos

- Especificação EMV Contactless (EMVCo)
- Comunidade Android NFC
- Bootstrap Icons
- Inter & JetBrains Mono fonts

---

## 📊 Status do Projeto

🟢 **Ativo** - Em desenvolvimento contínuo

**Última atualização**: Abril 2026

---

**Feito com ⚡ por desenvolvedores, para desenvolvedores**
