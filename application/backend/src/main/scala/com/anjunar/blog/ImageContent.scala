package com.anjunar.blog

import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import java.util.Locale
import java.util.concurrent.Semaphore
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import scala.util.control.NonFatal

final case class DecodedImage(contentType: String, width: Int, height: Int, bytes: Array[Byte])

object ImageContent {
  val MaxBytes = 5 * 1024 * 1024
  val MaxStoredBytes = 8 * 1024 * 1024
  val MaxPixels = 12_000_000L
  val AllowedTypes: Set[String] = Set("image/jpeg", "image/png")
  private val decoding = new Semaphore(2)

  def normalizedType(value: String): String =
    Option(value).getOrElse("").takeWhile(_ != ';').trim.toLowerCase(Locale.ROOT)

  def read(input: InputStream, declaredType: String): DecodedImage = {
    val contentType = normalizedType(declaredType)
    if (!AllowedTypes.contains(contentType)) throw new ApiProblem(415, "Choose a JPEG or PNG image.")
    if (!decoding.tryAcquire())
      throw new WebApplicationException(Response.status(429).header("Retry-After", "5").build())
    try {
      val bytes = input.readNBytes(MaxBytes + 1)
      if (bytes.length > MaxBytes) throw new ApiProblem(413, "Images may be at most 5 MiB.")
      if (bytes.isEmpty) Problem.invalidField("coverImage", "Choose a nonempty image.")
      decode(bytes, contentType)
    } finally decoding.release()
  }

  private def decode(bytes: Array[Byte], contentType: String): DecodedImage = {
    val input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))
    try {
      val readers = ImageIO.getImageReaders(input)
      if (!readers.hasNext) Problem.invalidField("coverImage", "The file is not a decodable JPEG or PNG.")
      val reader = readers.next()
      try {
        reader.setInput(input, true, true)
        val format = reader.getFormatName.toLowerCase(Locale.ROOT)
        val expected = if (contentType == "image/jpeg") Set("jpeg", "jpg") else Set("png")
        if (!expected.contains(format)) Problem.invalidField("coverImage", "The image does not match its content type.")
        val width = reader.getWidth(0)
        val height = reader.getHeight(0)
        if (width < 1 || height < 1 || width > 8000 || height > 8000 || width.toLong * height > MaxPixels)
          Problem.invalidField("coverImage", "Use an image up to 12 megapixels and 8000 pixels per side.")
        val image = reader.read(0)
        if (image == null) Problem.invalidField("coverImage", "The image could not be decoded.")
        val output = new LimitedOutput()
        // Write pixels only: discard source metadata, extra frames and trailing content.
        if (!ImageIO.write(image, if (contentType == "image/jpeg") "jpeg" else "png", output))
          Problem.invalidField("coverImage", "The image could not be stored.")
        DecodedImage(contentType, width, height, output.toByteArray)
      } finally reader.dispose()
    } catch {
      case error: ApiProblem => throw error
      case NonFatal(_) => Problem.invalidField("coverImage", "The image could not be decoded.")
    } finally input.close()
  }

  private class LimitedOutput extends ByteArrayOutputStream {
    private def reserve(amount: Int): Unit =
      if (count.toLong + amount > MaxStoredBytes)
        throw new ApiProblem(413, "The decoded image is too large to store.")
    override def write(value: Int): Unit = { reserve(1); super.write(value) }
    override def write(bytes: Array[Byte], offset: Int, length: Int): Unit = {
      reserve(length); super.write(bytes, offset, length)
    }
  }
}
