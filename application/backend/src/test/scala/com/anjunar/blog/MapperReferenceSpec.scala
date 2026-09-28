package com.anjunar.blog

import com.anjunar.json.mapper.{EntityLoader, JsonMapper}
import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.json.mapper.provider.EntityProvider
import com.anjunar.scala.universe.TypeResolver
import jakarta.json.bind.annotation.JsonbProperty
import jakarta.validation.Validation
import org.scalatest.funsuite.AnyFunSuite

import java.util.UUID
import scala.util.Using

class ReferenceProbe {
  @JsonbProperty var author: ReferenceAuthor = null
}

class ReferenceAuthor extends EntityProvider {
  @JsonbProperty var id: UUID = UUID.randomUUID()
  @JsonbProperty var version: Long = 0L
  @JsonbProperty var name: String = "An author"
}

class MapperReferenceSpec extends AnyFunSuite {
  private def assign(post: ReferenceProbe, target: ReferenceAuthor): Unit =
    Using.resource(Validation.buildDefaultValidatorFactory()) { factory =>
      val loader = new EntityLoader {
        override def load(id: UUID, clazz: Class[?]): Any = {
          assert(id == target.id && clazz == classOf[ReferenceAuthor])
          target
        }
      }
      val json = JsonParser.parse(s"""{"author":{"id":"${target.id}"}}""")
      JsonMapper.prepare(json, post, TypeResolver.resolve(classOf[ReferenceProbe]), null,
        loader, [T] => (clazz: Class[T]) => clazz.getConstructor().newInstance(),
        factory.getValidator).applyChanges()
    }

  test("the mapper resolves a new to-one EntityProvider reference through its loader") {
    val post = new ReferenceProbe()
    val target = new ReferenceAuthor()
    assign(post, target)
    assert(post.author eq target)
  }

  test("the mapper replaces an existing to-one reference when its submitted identity changes") {
    val post = new ReferenceProbe()
    post.author = new ReferenceAuthor()
    val target = new ReferenceAuthor()
    assign(post, target)
    assert(post.author eq target)
  }
}
