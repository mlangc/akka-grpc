import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.apache.commons.io.FileUtils

object LaunchScripts {
  private val BatScript =
    "@echo off\r\n" +
    "set jarPath=%~dpn0.jar\r\n" +
    "set jarPath=%jarPath:-bat.jar=-assembly.jar%\r\n" +
    "java -jar %JAVA_OPTS% \"%jarPath%\" %*\r\n" +
    "exit /B %errorlevel%\r\n"

  def mkBatScript(): File = {
    val file = Files.createTempFile("akka-grpc-", ".tmp").toFile
    file.deleteOnExit()
    FileUtils.writeStringToFile(file, BatScript, StandardCharsets.UTF_8)
    file
  }
}
