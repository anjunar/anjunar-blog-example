package com.anjunar.blog

import org.scalatest.funsuite.AnyFunSuite

import java.awt.image.BufferedImage
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets.UTF_8
import javax.imageio.ImageIO

object TestImages {
  def bytes(format: String = "png", width: Int = 4, height: Int = 3): Array[Byte] = {
    val image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    image.setRGB(0, 0, 0x336699)
    val output = new ByteArrayOutputStream()
    assert(ImageIO.write(image, format, output))
    output.toByteArray
  }
}

class ImageContentSpec extends AnyFunSuite {
  private def read(bytes: Array[Byte], contentType: String = "image/png"): DecodedImage =
    ImageContent.read(new ByteArrayInputStream(bytes), contentType)

  test("JPEG and PNG are decoded and written as pixels without appended content") {
    for ((format, contentType) <- Seq(("png", "image/png"), ("jpeg", "image/jpeg"))) {
      val clean = TestImages.bytes(format)
      val result = read(clean ++ "trailing source metadata".getBytes(UTF_8), contentType)
      assert(result.width == 4 && result.height == 3 && result.contentType == contentType)
      assert(!new String(result.bytes, UTF_8).contains("trailing source metadata"))
      assert(ImageIO.read(new ByteArrayInputStream(result.bytes)).getWidth == 4)
    }
  }

  test("unsupported, empty, truncated and mismatched image bodies fail safely") {
    assert(intercept[ApiProblem](read(TestImages.bytes(), "image/svg+xml")).status == 415)
    for (bytes <- Seq(Array.emptyByteArray, "<svg/>".getBytes(UTF_8), TestImages.bytes().take(24)))
      assert(intercept[ApiProblem](read(bytes)).status == 400)
    assert(intercept[ApiProblem](read(TestImages.bytes(), "image/jpeg")).status == 400)
  }

  test("byte and dimension limits apply before a full image is accepted") {
    assert(intercept[ApiProblem](read(new Array[Byte](ImageContent.MaxBytes + 1))).status == 413)
    val tooWide = TestImages.bytes(width = 8001, height = 1)
    assert(intercept[ApiProblem](read(tooWide)).status == 400)
    val tooManyPixels = TestImages.bytes(width = 4000, height = 3001)
    assert(intercept[ApiProblem](read(tooManyPixels)).status == 400)
  }
}
