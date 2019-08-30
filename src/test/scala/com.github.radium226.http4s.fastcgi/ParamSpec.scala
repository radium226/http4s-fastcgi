package com.github.radium226.http4s.fastcgi

import java.io.{ByteArrayOutputStream, DataOutputStream}
import java.nio.ByteBuffer

import com.github.radium226.fastcgi.FastCGI
import org.apache.commons.io.HexDump

/*
"""
    Encodes a name/value pair.
    The encoded string is returned.
    """
    nameLength = len(name)
    if nameLength < 128:
        s = bytes([nameLength])
    else:
        s = struct.pack('!L', nameLength | 0x80000000)

    valueLength = len(value)
    if valueLength < 128:
        s += bytes([valueLength])
    else:
        s += struct.pack('!L', valueLength | 0x80000000)

    return s + name + value
 */

class ParamSpec extends FastCGISpec {

  var _requestId: Int = -1

  def requestId: Int = {
    _requestId += 1
    _requestId
  }


  "We" should "be able to produce the right byte array" in {
    val paramKey = "SCRIPT_FILENAME"
    val paramValue = "/usr/lib/git-core/git-http-backend"

    HexDump.dump(param(paramKey, paramValue), 0, System.out, 0)
  }

  def record(t: Int, l: Int): Array[Byte] = {
    Array(FastCGI.version, t, requestId >> 8, requestId, l & 0xff >> 8, l & 0xff, 0, 0).map(_.toByte)
  }

  def param(key: String, value: String): Array[Byte] = {
    val byteArrayOutputStream = new ByteArrayOutputStream()
    val dataOutputStream = new DataOutputStream(byteArrayOutputStream)

    val kl = key.length
    if (kl < 128) dataOutputStream.writeByte(kl)
    else dataOutputStream.writeLong(kl | 0x80000000)

    val vl = value.length
    if (vl < 128) dataOutputStream.writeByte(vl)
    else dataOutputStream.writeLong(vl | 0x80000000)

    dataOutputStream.writeBytes(key)
    dataOutputStream.writeBytes(value)

    val byteArray = byteArrayOutputStream.toByteArray

    byteArray
  }

  def length(l: Int): Array[Byte] = {
    Array() //(if (l < 0x80) Array(l) else Array(0x80 | l >> 24, l >> 16, l >> 8, l)).map(_.toByte)
  }

}
