/*val nuProcessHandler = new NuProcessHandler {

        override def onPreStart(nuProcess: NuProcess): Unit = {
          println(s"onPreStart(${nuProcess})")
        }

        override def onStart(nuProcess: NuProcess): Unit = {
          println(s"onStart(${nuProcess})")
        }

        override def onExit(exitCode: Int): Unit = {
          println(s"onExit(${exitCode})")
        }

        override def onStdout(byteBuffer: ByteBuffer, closed: Boolean): Unit = {
          println(s"onStdout(closed=${closed})")
          if (!closed) {
            F.runAsync({
              F.delay({
                val count = byteBuffer.remaining()
                //println(s"count=${count}")
                val buffer = Array.ofDim[Byte](count)
                byteBuffer.get(buffer)
                //println(new String(buffer))
                buffer
              }).flatMap(_.toList.traverse(outputByteQueue.enqueue1))
            })(_ => IO.unit).unsafeRunSync()
          }
        }

        override def onStderr(buffer: ByteBuffer, closed: Boolean): Unit = {
          buffer.position(buffer.limit())
        }

        override def onStdinReady(buffer: ByteBuffer): Boolean = {
          //println(s" --> onStdinReady()")
          F.runAsync({
            for {
              byte <- inputByteQueue.dequeue1
              //_     = println(s"onStdinReady.byte=${byte}")
              _    <- F.delay(buffer.put(byte))
              _    <- F.delay(buffer.flip())
            } yield ()
          })(_ => IO.unit).unsafeRunSync
          //println(s" <-- onStdinReady()")
          false
        }

      }

      Stream.eval(F.delay({
        val nuProcessBuilder = new NuProcessBuilder(command.asJava)
        nuProcessBuilder.setProcessListener(nuProcessHandler)
        nuProcessBuilder.start
      })).flatMap({ nuProcess =>
        outputByteQueue.dequeue.unchunk concurrently inputBytes.evalTap({ inputByte =>
          inputByteQueue.enqueue1(inputByte) *> F.delay(nuProcess.wantWrite())
        }).unchunk
      })*/