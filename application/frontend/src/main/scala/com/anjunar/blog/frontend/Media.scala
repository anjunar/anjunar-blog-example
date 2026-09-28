package com.anjunar.blog.frontend

import ui.core.state.Property
import ui.json.{JsonId, JsonIgnore, JsonProperty}

import scala.annotation.meta.field

final class Media {
  @JsonId val id: Property[String] = Property("")
  @JsonIgnore(deserializable = true) val version: Property[Long] = Property(-1L)
  @JsonIgnore(deserializable = true) val name: Property[String] = Property("")
  @JsonIgnore(deserializable = true) val contentType: Property[String] = Property("")
  @JsonIgnore(deserializable = true) val width: Property[Int] = Property(0)
  @JsonIgnore(deserializable = true) val height: Property[Int] = Property(0)
  @JsonIgnore(deserializable = true) val byteSize: Property[Long] = Property(0L)

  @JsonIgnore()
  def source: String = {
    require(id.get.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"),
      "Invalid image identity")
    s"/service/media/${id.get}"
  }
}

final class MediaData(var data: Media = null,
    @(JsonProperty @field)("$links") var links: Seq[ApiLink] = Seq.empty)
