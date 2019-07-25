import java.io.{ File, FileInputStream, FileOutputStream }
import java.nio.file.Files

object Assemblies {

  /**
   * Generates a Windows bat compatible assembly
   *
   * Note: The implementation assumes that the passed assembly has been generated by
   * sbt-assembly using `defaultUniversalScript(shebang = true)`
   */
  def mkBatAssembly(assembly: File): File = {
    val file = Files.createTempFile("akka-grpc-", ".tmp").toFile

    file.deleteOnExit()
    copySkippingUntil('@'.toByte, assembly, file)
    file
  }

  private def copySkippingUntil(b: Byte, src: File, dst: File): Unit = {
    val in = new FileInputStream(src)
    try {
      val out = new FileOutputStream(dst, false)
      val foundSkipByte = Iterator.continually(in.read()).takeWhile(_ >= 0).dropWhile(_ != b.toInt).nonEmpty

      try {
        if (foundSkipByte)
          out.write(b.toInt)

        val buffer = new Array[Byte](1024)
        var continue = true && foundSkipByte
        while (continue) {
          val r = in.read(buffer)
          if (r < 0) continue = false
          else out.write(buffer, 0, r)
        }
      } finally {
        out.close()
      }
    } finally {
      in.close()
    }
  }
}
