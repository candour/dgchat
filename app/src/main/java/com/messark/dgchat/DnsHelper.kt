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
        
        val allBytes = mutableListOf<Byte>()
        for (rec in txtRecords) {
            val payloadBytes = rec.payload.toByteArray()
            var i = 0
            while (i < payloadBytes.size) {
                val len = payloadBytes[i].toInt() and 0xFF
                i++
                if (i + len <= payloadBytes.size) {
                    for (j in 0 until len) {
                        allBytes.add(payloadBytes[i + j])
                    }
                    i += len
                } else {
                    break
                }
            }
        }
        val combined = allBytes.toByteArray()

        val cname = response.answerSection.find { it.type == Record.TYPE.CNAME }?.let {
            val cnameRecord = it.payload as CNAME
            cnameRecord.target.toString()
        }

        return combined to cname
    }
}
