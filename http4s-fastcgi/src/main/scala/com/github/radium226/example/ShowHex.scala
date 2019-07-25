package com.github.radium226.example

import java.io.ByteArrayOutputStream
import java.util
import java.nio.ByteBuffer

import org.apache.commons.codec.binary.Hex
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.io.OutputStream

import org.apache.commons.io.HexDump
import org.http4s.{Request, Status}
import cats._
import org.http4s.headers.`Content-Type`

object FastCGI {

  val BeginRequest = 1
  val EndRequest = 3
  val Params = 4
  val StdIn = 5
  val StdOut = 6
  val StdErr = 7
  val Responder = 1
  val Version = 1
  val KeepConnected = 1
  val RequestComplete = 0

}

object ShowHex extends App {

  val requestID = new AtomicInteger(1)

  @throws[IOException]
  private def writeHeader(ws: OutputStream, `type`: Int, length: Int): Short = {
    val id = requestID.getAndIncrement().toShort
    val pad = 0
    ws.write(FastCGI.Version)
    ws.write(`type`)
    ws.write(id >> 8)
    ws.write(id)
    ws.write(length >> 8)
    ws.write(length)
    ws.write(pad)
    ws.write(0)
    id
  }

  import java.io.IOException

  @throws[IOException]
  def addHeader(ws: OutputStream, key: String, value: String): Unit = {
    if (value != null) {
      val keyLen = key.length
      val valLen = value.length
      var len = keyLen + valLen
      if (keyLen < 0x80) len += 1
      else len += 4
      if (valLen < 0x80) len += 1
      else len += 4
      writeHeader(ws, FastCGI.Params, len)
      if (keyLen < 0x80) ws.write(keyLen)
      else {
        ws.write(0x80 | keyLen >> 24)
        ws.write(keyLen >> 16)
        ws.write(keyLen >> 8)
        ws.write(keyLen)
      }
      if (valLen < 0x80) ws.write(valLen)
      else {
        ws.write(0x80 | valLen >> 24)
        ws.write(valLen >> 16)
        ws.write(valLen >> 8)
        ws.write(valLen)
      }
      ws.write(key.getBytes)
      ws.write(value.getBytes)
    }
  }

  import org.jfastcgi.api.RequestAdapter
  import org.jfastcgi.client.FastCGIHandler
  import org.jfastcgi.client.FastCGIHandlerFactory
  import java.io.IOException
  import java.net.URLEncoder
  import java.util

  @throws[IOException]
  private def setEnvironment(ws: OutputStream, req: Request[Id]): Unit = {
    /*
fastcgi_param  QUERY_STRING       $query_string;
fastcgi_param  REQUEST_METHOD     $request_method;
fastcgi_param  CONTENT_TYPE       $content_type;
fastcgi_param  CONTENT_LENGTH     $content_length;

fastcgi_param  SCRIPT_NAME        $fastcgi_script_name;
fastcgi_param  REQUEST_URI        $request_uri;
fastcgi_param  DOCUMENT_URI       $document_uri;
fastcgi_param  DOCUMENT_ROOT      $document_root;
fastcgi_param  SERVER_PROTOCOL    $server_protocol;
fastcgi_param  REQUEST_SCHEME     $scheme;
fastcgi_param  HTTPS              $https if_not_empty;

fastcgi_param  GATEWAY_INTERFACE  CGI/1.1;
fastcgi_param  SERVER_SOFTWARE    nginx/$nginx_version;

fastcgi_param  REMOTE_ADDR        $remote_addr;
fastcgi_param  REMOTE_PORT        $remote_port;
fastcgi_param  SERVER_ADDR        $server_addr;
fastcgi_param  SERVER_PORT        $server_port;
fastcgi_param  SERVER_NAME        $server_name;
     */

    if (req.queryString != null) addHeader(ws, "REQUEST_URI", req.uri.toString() + "?" + req.queryString)
    else addHeader(ws, "REQUEST_URI", req.uri.toString())
    //if (req.headers(`Content-Type`) != null) addHeader(ws, "CONTENT_TYPE", req.)



    addHeader(ws, "REQUEST_METHOD", req.method.name)
    addHeader(ws, "SERVER_SOFTWARE", req.serverSoftware.product)
    addHeader(ws, "SERVER_NAME", req.serverAddr)
    addHeader(ws, "SERVER_PORT", req.serverPort.toString)
    addHeader(ws, "REMOTE_ADDR", req.remoteAddr.getOrElse(""))
    addHeader(ws, "REMOTE_HOST", req.remoteHost.getOrElse(""))
  }

  val request = Request[Id]()

  val keepAlive = false

  val byteArrayOutputStream = new ByteArrayOutputStream()
  writeHeader(byteArrayOutputStream, FastCGI.BeginRequest, 8)

  val role = FastCGI.Responder

  byteArrayOutputStream.write(role >> 8)
  byteArrayOutputStream.write(role)

  byteArrayOutputStream.write(if (keepAlive) FastCGI.KeepConnected else 0); // flags

  for (i <- 0 to 4) {
    byteArrayOutputStream.write(0);
  }

  setEnvironment(byteArrayOutputStream, request)


  val bytes = byteArrayOutputStream.toByteArray

  HexDump.dump(bytes, 0, System.out, 0)

}
