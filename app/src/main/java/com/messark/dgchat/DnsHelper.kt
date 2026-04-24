package com.messark.dgchat

import org.minidns.dnsmessage.DnsMessage
import org.minidns.dnsname.DnsName
import org.minidns.record.CNAME
import org.minidns.record.Record
import org.minidns.record.TXT
import org.minidns.DnsClient
import org.minidns.dnsmessage.Question
import java.net.InetAddress

class DnsHelper(private val dnsServer: String?) {

    private val client = DnsClient()

    fun queryTxt(name: String): Pair<ByteArray, String?> {
        val dnsName = DnsName.from(name)
        val question = Question(dnsName, Record.TYPE.TXT)

        val dnsQueryResult = if (dnsServer != null && dnsServer.isNotBlank()) {
            val serverAddress = InetAddress.getByName(dnsServer)
            client.query(question, serverAddress)
        } else {
            client.query(question)
        }

        if (dnsQueryResult == null) return byteArrayOf() to null

        val response = dnsQueryResult.response
        if (response.responseCode != DnsMessage.RESPONSE_CODE.NO_ERROR) {
            return byteArrayOf() to null
        }

        val txtRecords = response.answerSection.filter { it.type == Record.TYPE.TXT }
        val combined = txtRecords.flatMap { record ->
            val txt = record.payload as TXT
            txt.characterStrings.flatMap { decodeTxtEscapes(it).toList() }
        }.toByteArray()

        val cname = response.answerSection.find { it.type == Record.TYPE.CNAME }?.let {
            (it.payload as CNAME).target.toString()
        }

        return combined to cname
    }

    private fun decodeTxtEscapes(s: String): ByteArray {
        val result = mutableListOf<Byte>()
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                if (i + 3 < s.length && s[i+1].isDigit() && s[i+2].isDigit() && s[i+3].isDigit()) {
                    val value = s.substring(i + 1, i + 4).toInt()
                    result.add(value.toByte())
                    i += 4
                } else {
                    result.add(s[i+1].code.toByte())
                    i += 2
                }
            } else {
                result.add(s[i].code.toByte())
                i++
            }
        }
        return result.toByteArray()
    }
}
