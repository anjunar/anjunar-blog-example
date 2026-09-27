package com.anjunar.blog

import java.security.{MessageDigest, SecureRandom}
import java.util.{Arrays, Base64}
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHash {
  private val iterations = 600000
  private val random = new SecureRandom()
  private val encoder = Base64.getUrlEncoder.withoutPadding()
  private val decoder = Base64.getUrlDecoder

  def acceptable(password: String): Boolean =
    password != null && password.length >= 15 && password.length <= 128

  def create(password: String): String = {
    require(acceptable(password), "Use a password with 15 to 128 characters")
    val salt = new Array[Byte](16)
    random.nextBytes(salt)
    s"pbkdf2-sha256$$$iterations$$${encoder.encodeToString(salt)}$$${encoder.encodeToString(derive(password, salt))}"
  }

  def verify(password: String, stored: String): Boolean = {
    if (password == null || password.length > 128 || stored == null || stored.length > 200) return false
    val parts = stored.split("\\$", -1)
    if (parts.length != 4 || parts(0) != "pbkdf2-sha256" || parts(1) != iterations.toString) return false
    try {
      val salt = decoder.decode(parts(2))
      val expected = decoder.decode(parts(3))
      salt.length == 16 && expected.length == 32 &&
        MessageDigest.isEqual(expected, derive(password, salt))
    } catch {
      case _: IllegalArgumentException => false
    }
  }

  // Unknown accounts still perform one password derivation.
  lazy val decoy: String = create("an-unusable-random-account-" + encoder.encodeToString(random.generateSeed(24)))

  private def derive(password: String, salt: Array[Byte]): Array[Byte] = {
    val chars = password.toCharArray
    val spec = new PBEKeySpec(chars, salt, iterations, 256)
    try SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded
    finally {
      spec.clearPassword()
      Arrays.fill(chars, '\u0000')
    }
  }
}
