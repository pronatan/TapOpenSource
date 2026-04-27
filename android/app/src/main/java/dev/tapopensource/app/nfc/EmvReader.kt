package dev.tapopensource.app.nfc

import android.nfc.tech.IsoDep
import dev.tapopensource.app.log.LogClient as Log

/**
 * TapOpenSource — EMV Reader
 *
 * Lê dados básicos de cartões bancários EMV contactless via APDU commands.
 * Fluxo: SELECT PPSE → SELECT AID → GET PROCESSING OPTIONS → READ RECORD
 *
 * Referência: EMV Contactless Book C-2 / C-3 (Visa/Mastercard)
 */
object EmvReader {

    // ── APDU Commands ────────────────────────────────────────
    // SELECT PPSE (Proximity Payment System Environment)
    private val SELECT_PPSE = byteArrayOf(
        0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(),
        0x0E.toByte(),
        // "2PAY.SYS.DDF01" em ASCII
        0x32, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59, 0x53,
        0x2E, 0x44, 0x44, 0x46, 0x30, 0x31,
        0x00.toByte()
    )

    // GET PROCESSING OPTIONS (PDOL vazio)
    private val GET_PROCESSING_OPTIONS = byteArrayOf(
        0x80.toByte(), 0xA8.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x02.toByte(), 0x83.toByte(), 0x00.toByte(), 0x00.toByte()
    )

    /** Constrói comando GPO com PDOL customizado se necessário */
    private fun buildGPO(pdol: ByteArray?): ByteArray {
        if (pdol == null || pdol.isEmpty()) {
            // PDOL vazio (padrão)
            return GET_PROCESSING_OPTIONS
        }

        // Constrói PDOL data com valores realistas
        val pdolData = mutableListOf<Byte>()
        var i = 0
        while (i < pdol.size - 1) {
            val tag1 = pdol[i].toInt() and 0xFF
            val tag2 = if ((tag1 and 0x1F) == 0x1F && i + 1 < pdol.size) pdol[i + 1].toInt() and 0xFF else -1
            val tagBytes = if (tag2 >= 0) 2 else 1
            
            if (i + tagBytes >= pdol.size) break
            val len = pdol[i + tagBytes].toInt() and 0xFF
            
            // Preenche com valores apropriados baseado na tag
            when {
                // 9F66 - Terminal Transaction Qualifiers (4 bytes)
                // Bit settings: Contactless EMV mode, online capable, CVM supported
                tag1 == 0x9F && tag2 == 0x66 -> {
                    pdolData.add(0xF6.toByte()) // Byte 1: Contactless + MSD + qVSDC + EMV
                    pdolData.add(0x20.toByte()) // Byte 2: Online PIN supported
                    pdolData.add(0xC0.toByte()) // Byte 3: CVM required
                    pdolData.add(0x00.toByte()) // Byte 4: Reserved
                    repeat(len - 4) { pdolData.add(0x00) }
                }
                // 9F02 - Amount Authorized (6 bytes BCD) - será preenchido com valor real
                tag1 == 0x9F && tag2 == 0x02 -> repeat(len) { pdolData.add(0x00) }
                // 9F03 - Amount Other (6 bytes) - cashback, sempre 0
                tag1 == 0x9F && tag2 == 0x03 -> repeat(len) { pdolData.add(0x00) }
                // 9F1A - Terminal Country Code (2 bytes) - Brasil = 0x0076
                tag1 == 0x9F && tag2 == 0x1A -> {
                    pdolData.add(0x00); pdolData.add(0x76.toByte())
                    repeat(len - 2) { pdolData.add(0x00) }
                }
                // 95 - Terminal Verification Results (5 bytes)
                // Indica que o terminal suporta várias features
                tag1 == 0x95 -> {
                    pdolData.add(0x00.toByte()) // Byte 1
                    pdolData.add(0x00.toByte()) // Byte 2
                    pdolData.add(0x00.toByte()) // Byte 3
                    pdolData.add(0x00.toByte()) // Byte 4
                    pdolData.add(0x00.toByte()) // Byte 5
                    repeat(len - 5) { pdolData.add(0x00) }
                }
                // 5F2A - Transaction Currency Code (2 bytes) - BRL = 0x0986
                tag1 == 0x5F && tag2 == 0x2A -> {
                    pdolData.add(0x09); pdolData.add(0x86.toByte())
                    repeat(len - 2) { pdolData.add(0x00) }
                }
                // 9A - Transaction Date (3 bytes YYMMDD)
                tag1 == 0x9A -> {
                    val now = java.util.Calendar.getInstance()
                    val year = now.get(java.util.Calendar.YEAR) % 100
                    val month = now.get(java.util.Calendar.MONTH) + 1
                    val day = now.get(java.util.Calendar.DAY_OF_MONTH)
                    pdolData.add(((year / 10) * 16 + (year % 10)).toByte())  // BCD
                    pdolData.add(((month / 10) * 16 + (month % 10)).toByte()) // BCD
                    pdolData.add(((day / 10) * 16 + (day % 10)).toByte())     // BCD
                    repeat(len - 3) { pdolData.add(0x00) }
                }
                // 9C - Transaction Type (1 byte) - 0x00 = Purchase
                tag1 == 0x9C -> {
                    pdolData.add(0x00)
                    repeat(len - 1) { pdolData.add(0x00) }
                }
                // 9F37 - Unpredictable Number (4 bytes) - random criptograficamente seguro
                tag1 == 0x9F && tag2 == 0x37 -> {
                    val random = java.security.SecureRandom()
                    repeat(len) { pdolData.add(random.nextInt(256).toByte()) }
                }
                // 9F35 - Terminal Type (1 byte) - 0x22 = Contactless capable
                tag1 == 0x9F && tag2 == 0x35 -> {
                    pdolData.add(0x22.toByte())
                    repeat(len - 1) { pdolData.add(0x00) }
                }
                // 9F34 - CVM Results (3 bytes) - No CVM performed
                tag1 == 0x9F && tag2 == 0x34 -> {
                    pdolData.add(0x3F.toByte()) // No CVM required
                    pdolData.add(0x00.toByte())
                    pdolData.add(0x00.toByte())
                    repeat(len - 3) { pdolData.add(0x00) }
                }
                // 9F33 - Terminal Capabilities (3 bytes)
                tag1 == 0x9F && tag2 == 0x33 -> {
                    pdolData.add(0xE0.toByte()) // Manual key entry, Magnetic stripe, IC with contacts
                    pdolData.add(0xF8.toByte()) // Plaintext PIN, Enciphered PIN online, Signature, Enciphered PIN offline, No CVM
                    pdolData.add(0xC8.toByte()) // SDA, DDA, Card capture
                    repeat(len - 3) { pdolData.add(0x00) }
                }
                // 9F40 - Additional Terminal Capabilities (5 bytes)
                tag1 == 0x9F && tag2 == 0x40 -> {
                    pdolData.add(0xF0.toByte()) // Cash, Goods, Services, Cashback
                    pdolData.add(0x00.toByte())
                    pdolData.add(0xF0.toByte()) // Numeric keys, Function keys
                    pdolData.add(0x00.toByte())
                    pdolData.add(0x00.toByte())
                    repeat(len - 5) { pdolData.add(0x00) }
                }
                // Outros campos - preenche com zeros
                else -> repeat(len) { pdolData.add(0x00) }
            }
            
            i += tagBytes + 1
        }

        Log.info("nfc:pdol_built", mapOf("len" to pdolData.size, "hex" to pdolData.toByteArray().toHex().take(100)))

        // Monta comando: 80 A8 00 00 Lc 83 Ld [PDOL data] 00
        val dataLen = pdolData.size
        return byteArrayOf(
            0x80.toByte(), 0xA8.toByte(), 0x00.toByte(), 0x00.toByte(),
            (dataLen + 2).toByte(), 0x83.toByte(), dataLen.toByte()
        ) + pdolData.toByteArray() + byteArrayOf(0x00)
    }

    data class CardData(
        val aid: String,           // Application Identifier
        val pan: String,           // PAN mascarado (ex: 4111 **** **** 1111)
        val panComplete: String,   // PAN completo (SENSÍVEL - use apenas para gateway)
        val expiry: String,        // MMYY
        val cardholderName: String,
        val track2: String,        // Track 2 Equivalent Data (mascarado)
        val aip: String,           // Application Interchange Profile
        val rawSerial: String,     // UID da tag
    )

    /**
     * Executa o fluxo EMV completo e retorna os dados do cartão.
     * @throws EmvException em caso de falha em qualquer etapa
     */
    fun read(isoDep: IsoDep): CardData {
        isoDep.timeout = 5000

        // 1. SELECT PPSE
        Log.info("nfc:apdu_send", mapOf("cmd" to "SELECT_PPSE"))
        val ppseResp = isoDep.transceive(SELECT_PPSE)
        Log.info("nfc:apdu_recv", mapOf("cmd" to "SELECT_PPSE", "sw" to getSW(ppseResp), "len" to ppseResp.size))
        checkSW(ppseResp, "SELECT PPSE")

        // 2. Extrai AID da resposta PPSE (tenta todos os AIDs disponíveis)
        val aids = extractAllAids(ppseResp)
        if (aids.isEmpty()) {
            Log.info("nfc:ppse_raw", mapOf("hex" to ppseResp.toHex().take(200)))
            throw EmvException("Nenhum AID encontrado na resposta PPSE")
        }
        Log.info("nfc:aids_found", mapOf("count" to aids.size, "aids" to aids.map { it.toHex() }))

        // 3. Tenta cada AID até encontrar um que funcione
        var lastError: String? = null
        for ((index, aid) in aids.withIndex()) {
            try {
                Log.info("nfc:trying_aid", mapOf("index" to index, "aid" to aid.toHex()))
                
                // SELECT AID
                val selectAid = buildSelectAid(aid)
                val aidResp = isoDep.transceive(selectAid)
                Log.info("nfc:apdu_recv", mapOf("cmd" to "SELECT_AID", "sw" to getSW(aidResp), "len" to aidResp.size))
                checkSW(aidResp, "SELECT AID")

                // Extrai PDOL da resposta SELECT AID (tag 9F38)
                val pdol = extractTag2Byte(aidResp, 0x9F.toByte(), 0x38.toByte())
                if (pdol != null) {
                    Log.info("nfc:pdol_found", mapOf("len" to pdol.size, "hex" to pdol.toHex()))
                }

                // 4. GET PROCESSING OPTIONS
                val gpoCmd = buildGPO(pdol)
                val gpoResp = isoDep.transceive(gpoCmd)
                Log.info("nfc:apdu_recv", mapOf("cmd" to "GPO", "sw" to getSW(gpoResp), "len" to gpoResp.size, "hex" to gpoResp.toHex().take(100)))
                checkSW(gpoResp, "GET PROCESSING OPTIONS")

                val aip = extractTag(gpoResp, 0x82.toByte())?.toHex() ?: "0000"
                var afl = extractTag(gpoResp, 0x94.toByte())
                
                // Tenta extrair AFL do formato alternativo (tag 80 - Response Message Template)
                if (afl == null) {
                    afl = extractAflFromRmTlv(gpoResp)
                }
                
                // Tenta extrair AFL do formato 77 (Response Message Template Format 2)
                if (afl == null) {
                    extractTag(gpoResp, 0x77.toByte())?.let { t77 ->
                        afl = extractTag(t77, 0x94.toByte())
                        Log.info("nfc:afl_from_77", mapOf("len" to (afl?.size ?: 0)))
                    }
                }
                
                Log.info("nfc:gpo_parsed", mapOf("aip" to aip, "afl_len" to (afl?.size ?: 0)))

                // 5. READ RECORD — lê os registros indicados pelo AFL
                val records = readRecords(isoDep, afl)
                Log.info("nfc:records_read", mapOf("count" to records.size))

                // 6. Extrai dados dos registros
                val pan          = extractPan(records)
                val expiry       = extractExpiry(records)
                val name         = extractName(records)
                val track2       = extractTrack2(records)
                val rawSerial    = isoDep.tag?.id?.toHex() ?: ""

                Log.info("nfc:data_extracted", mapOf(
                    "pan_len" to pan.length,
                    "expiry" to expiry,
                    "name" to name
                ))

                return CardData(
                    aid           = aid.toHex(),
                    pan           = maskPan(pan),
                    panComplete   = pan,  // PAN completo (SENSÍVEL)
                    expiry        = expiry,
                    cardholderName = name,
                    track2        = maskTrack2(track2),
                    aip           = aip,
                    rawSerial     = rawSerial,
                )
            } catch (e: Exception) {
                lastError = e.message
                Log.warn("nfc:aid_failed", mapOf("aid" to aid.toHex(), "error" to (e.message ?: "unknown")))
                // Continua para o próximo AID
            }
        }

        // Se chegou aqui, nenhum AID funcionou
        throw EmvException("Nenhum AID funcionou. Último erro: $lastError")
    }

    // ── APDU Helpers ─────────────────────────────────────────

    private fun buildSelectAid(aid: ByteArray): ByteArray =
        byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid + byteArrayOf(0x00)

    private fun getSW(response: ByteArray): String {
        if (response.size < 2) return "EMPTY"
        val sw1 = response[response.size - 2].toInt() and 0xFF
        val sw2 = response[response.size - 1].toInt() and 0xFF
        return "%02X%02X".format(sw1, sw2)
    }

    private fun checkSW(response: ByteArray, step: String) {
        if (response.size < 2) throw EmvException("$step: resposta vazia")
        val sw1 = response[response.size - 2].toInt() and 0xFF
        val sw2 = response[response.size - 1].toInt() and 0xFF
        if (sw1 != 0x90 || sw2 != 0x00) {
            throw EmvException("$step: SW=%02X%02X".format(sw1, sw2))
        }
    }

    private fun readRecords(isoDep: IsoDep, afl: ByteArray?): List<ByteArray> {
        val records = mutableListOf<ByteArray>()
        if (afl == null || afl.size % 4 != 0) {
            Log.warn("nfc:read_records_skip", mapOf("reason" to "invalid_afl", "afl_size" to (afl?.size ?: 0)))
            return records
        }

        var i = 0
        while (i < afl.size) {
            val sfi      = (afl[i].toInt() and 0xFF) ushr 3
            val firstRec = afl[i + 1].toInt() and 0xFF
            val lastRec  = afl[i + 2].toInt() and 0xFF

            Log.info("nfc:read_records_range", mapOf("sfi" to sfi, "first" to firstRec, "last" to lastRec))

            for (rec in firstRec..lastRec) {
                val cmd = byteArrayOf(
                    0x00, 0xB2.toByte(),
                    rec.toByte(),
                    ((sfi shl 3) or 4).toByte(),
                    0x00
                )
                try {
                    val resp = isoDep.transceive(cmd)
                    val sw = getSW(resp)
                    Log.info("nfc:read_record", mapOf("sfi" to sfi, "rec" to rec, "sw" to sw, "len" to resp.size))
                    if (resp.size >= 2 && sw == "9000") {
                        records.add(resp)
                    }
                } catch (e: Exception) {
                    Log.warn("nfc:read_record_error", mapOf("sfi" to sfi, "rec" to rec, "error" to (e.message ?: "unknown")))
                }
            }
            i += 4
        }
        return records
    }

    // ── TLV Parsers ───────────────────────────────────────────

    /** Extrai TODOS os AIDs (tag 4F) da resposta PPSE — busca recursiva em templates */
    private fun extractAllAids(data: ByteArray): List<ByteArray> {
        val aids = mutableListOf<ByteArray>()

        // 1. Busca dentro de 6F → A5 → BF0C → 61 → 4F  (estrutura padrão EMV)
        extractTag(data, 0x6F.toByte())?.let { t6F ->
            extractTag(t6F, 0xA5.toByte())?.let { tA5 ->
                extractTag2Byte(tA5, 0xBF.toByte(), 0x0C.toByte())?.let { tBF0C ->
                    // Busca todos os templates 61 dentro do BF0C
                    findAllTemplates(tBF0C, 0x61.toByte()).forEach { t61 ->
                        extractTag(t61, 0x4F)?.let { aid ->
                            aids.add(aid)
                            Log.info("nfc:aid_found", mapOf("aid" to aid.toHex(), "source" to "BF0C_61"))
                        }
                    }
                }
            }
        }

        // 2. Busca direta em todos os templates 61 no payload completo (fallback)
        if (aids.isEmpty()) {
            findAllTemplates(data, 0x61.toByte()).forEach { t61 ->
                extractTag(t61, 0x4F)?.let { aid ->
                    aids.add(aid)
                    Log.info("nfc:aid_found", mapOf("aid" to aid.toHex(), "source" to "direct_61"))
                }
            }
        }

        // 3. Busca direta pela tag 4F (fallback final)
        if (aids.isEmpty()) {
            extractTag(data, 0x4F)?.let { aid ->
                aids.add(aid)
                Log.info("nfc:aid_found", mapOf("aid" to aid.toHex(), "source" to "direct_4F"))
            }
        }

        return aids
    }

    /** Extrai o primeiro AID (tag 4F) da resposta PPSE — busca recursiva em templates */
    @Deprecated("Use extractAllAids instead")
    private fun extractAid(data: ByteArray): ByteArray? {
        // 1. Direto
        extractTag(data, 0x4F)?.let { return it }

        // 2. Dentro de 6F → A5 → BF0C → 61 → 4F  (estrutura real Mastercard/Visa BR)
        extractTag(data, 0x6F.toByte())?.let { t6F ->
            extractTag(t6F, 0xA5.toByte())?.let { tA5 ->
                extractTag2Byte(tA5, 0xBF.toByte(), 0x0C.toByte())?.let { tBF0C ->
                    extractTag(tBF0C, 0x61.toByte())?.let { t61 ->
                        extractTag(t61, 0x4F)?.let { return it }
                    }
                    // Busca todos os 61 dentro do BF0C
                    findAllTemplates(tBF0C, 0x61.toByte()).forEach { t61 ->
                        extractTag(t61, 0x4F)?.let { return it }
                    }
                }
            }
        }

        // 3. Busca direta em todos os templates 61 no payload completo
        findAllTemplates(data, 0x61.toByte()).forEach { t61 ->
            extractTag(t61, 0x4F)?.let { return it }
        }

        return null
    }

    /** Retorna o conteúdo de todos os templates com a tag especificada */
    private fun findAllTemplates(data: ByteArray, tag: Byte): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        var i = 0
        while (i < data.size - 1) {
            val t = data[i]
            val lenByte = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else break
            val (len, lenBytes) = when {
                lenByte <= 0x7F -> Pair(lenByte, 1)
                lenByte == 0x81 && i + 2 < data.size -> Pair(data[i + 2].toInt() and 0xFF, 2)
                else -> { i++; continue }
            }
            if (t == tag && i + 1 + lenBytes + len <= data.size) {
                result.add(data.copyOfRange(i + 1 + lenBytes, i + 1 + lenBytes + len))
            }
            i += 1 + lenBytes + len
        }
        return result
    }

    /** Extrai AFL do template RM (tag 80) quando GPO retorna formato simples */
    private fun extractAflFromRmTlv(data: ByteArray): ByteArray? {
        val rm = extractTag(data, 0x80.toByte()) ?: return null
        Log.info("nfc:rm_found", mapOf("len" to rm.size, "hex" to rm.toHex()))
        
        // Formato: [AIP 2 bytes][AFL n bytes]
        if (rm.size <= 2) return null
        
        // AIP são os primeiros 2 bytes, AFL é o resto
        val afl = rm.copyOfRange(2, rm.size)
        Log.info("nfc:afl_from_rm", mapOf("len" to afl.size, "hex" to afl.toHex()))
        return afl
    }

    private fun extractPan(records: List<ByteArray>): String {
        for (rec in records) {
            val tag57 = extractTag(rec, 0x57)
            if (tag57 != null) {
                // Track 2 Equivalent: PAN separado por 'D'
                val hex = tag57.toHex()
                val dIdx = hex.indexOf('D')
                if (dIdx > 0) return hex.substring(0, dIdx).trimEnd('F', 'f')
            }
            val tag5A = extractTag(rec, 0x5A)
            if (tag5A != null) return tag5A.toHex().trimEnd('F', 'f')
        }
        return ""
    }

    private fun extractExpiry(records: List<ByteArray>): String {
        for (rec in records) {
            val tag57 = extractTag(rec, 0x57)
            if (tag57 != null) {
                val hex = tag57.toHex()
                val dIdx = hex.indexOf('D')
                if (dIdx > 0 && hex.length > dIdx + 4)
                    return hex.substring(dIdx + 1, dIdx + 5) // YYMM
            }
            val tag5F24 = extractTag2Byte(rec, 0x5F, 0x24)
            if (tag5F24 != null) return tag5F24.toHex() // YYMMDD
        }
        return ""
    }

    private fun extractName(records: List<ByteArray>): String {
        for (rec in records) {
            val tag5F20 = extractTag2Byte(rec, 0x5F, 0x20)
            if (tag5F20 != null) return String(tag5F20).trim()
        }
        return "CARDHOLDER"
    }

    private fun extractTrack2(records: List<ByteArray>): String {
        for (rec in records) {
            val tag57 = extractTag(rec, 0x57)
            if (tag57 != null) return tag57.toHex()
        }
        return ""
    }

    /** Parser TLV genérico para tag de 1 byte — BER-TLV completo */
    private fun extractTag(data: ByteArray, tag: Byte): ByteArray? {
        var i = 0
        while (i < data.size - 1) {
            val t = data[i]
            var tagBytes = 1

            // Pula tags multi-byte (bit 5 do primeiro byte = 1 indica tag longa)
            if ((t.toInt() and 0x1F) == 0x1F) {
                tagBytes++
                while (i + tagBytes < data.size && (data[i + tagBytes - 1].toInt() and 0x80) != 0) {
                    tagBytes++
                }
                // Não é a tag que procuramos, pula
                if (i + tagBytes >= data.size) break
                val lenByte = data[i + tagBytes].toInt() and 0xFF
                val (len, lenBytes) = when {
                    lenByte <= 0x7F -> Pair(lenByte, 1)
                    lenByte == 0x81 && i + tagBytes + 1 < data.size -> 
                        Pair(data[i + tagBytes + 1].toInt() and 0xFF, 2)
                    lenByte == 0x82 && i + tagBytes + 2 < data.size -> Pair(
                        ((data[i + tagBytes + 1].toInt() and 0xFF) shl 8) or 
                        (data[i + tagBytes + 2].toInt() and 0xFF), 3)
                    else -> { i++; continue }
                }
                i += tagBytes + lenBytes + len
                continue
            }

            // Tag de 1 byte - verifica se é a que procuramos
            if (i + 1 >= data.size) break
            val lenByte = data[i + 1].toInt() and 0xFF
            val (len, lenBytes) = when {
                lenByte <= 0x7F -> Pair(lenByte, 1)
                lenByte == 0x81 && i + 2 < data.size -> Pair(data[i + 2].toInt() and 0xFF, 2)
                lenByte == 0x82 && i + 3 < data.size -> Pair(
                    ((data[i + 2].toInt() and 0xFF) shl 8) or (data[i + 3].toInt() and 0xFF), 3)
                else -> { i++; continue }
            }

            if (t == tag && i + 1 + lenBytes + len <= data.size) {
                return data.copyOfRange(i + 1 + lenBytes, i + 1 + lenBytes + len)
            }

            i += 1 + lenBytes + len
        }
        return null
    }

    /** Parser TLV para tag de 2 bytes — BER-TLV completo */
    private fun extractTag2Byte(data: ByteArray, t1: Byte, t2: Byte): ByteArray? {
        var i = 0
        while (i < data.size - 2) {
            if (data[i] == t1 && data[i + 1] == t2) {
                val lenByte = data[i + 2].toInt() and 0xFF
                val (len, lenBytes) = when {
                    lenByte <= 0x7F -> Pair(lenByte, 1)
                    lenByte == 0x81 -> Pair(data[i + 3].toInt() and 0xFF, 2)
                    else -> return null
                }
                if (i + 2 + lenBytes + len <= data.size)
                    return data.copyOfRange(i + 2 + lenBytes, i + 2 + lenBytes + len)
            }
            i++
        }
        return null
    }

    // ── Masking ───────────────────────────────────────────────

    private fun maskPan(pan: String): String {
        if (pan.length < 8) return pan
        return pan.take(6) + "*".repeat(pan.length - 10) + pan.takeLast(4)
    }

    private fun maskTrack2(track2: String): String {
        val dIdx = track2.indexOf('D')
        if (dIdx < 0) return "****"
        return track2.take(6) + "****" + track2.substring(dIdx)
    }

    // ── Extension ─────────────────────────────────────────────
    private fun ByteArray.toHex() = joinToString("") { "%02X".format(it) }
}

class EmvException(message: String) : Exception(message)
