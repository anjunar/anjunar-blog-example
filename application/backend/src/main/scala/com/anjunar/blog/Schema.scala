package com.anjunar.blog

import com.anjunar.json.mapper.provider.DTO
import com.anjunar.json.mapper.schema.EntitySchema
import jakarta.json.bind.annotation.JsonbProperty
import jakarta.persistence.{EntityGraph as JpaEntityGraph}

import java.util
import scala.annotation.meta.field
import scala.jdk.CollectionConverters.*

class Schema(@(JsonbProperty @field) val entries: util.List[SchemaProperty]) extends DTO

class SchemaProperty(
    @(JsonbProperty @field) val name: String,
    @(JsonbProperty @field)("type") val typeName: String
) extends DTO

object Schema {
  // Describes the selected fields, not the caller's permissions.
  def forGraph(source: EntitySchema[?], graph: JpaEntityGraph[?], additional: Set[String] = Set.empty): Schema = {
    val selected = graph.getAttributeNodes.asScala.map(_.getAttributeName).toSet ++ additional
    val entries = source.properties.valuesIterator
      .filter(property => selected.contains(property.name))
      .map(property => new SchemaProperty(property.name, property.typeName))
      .toList.asJava
    new Schema(entries)
  }
}
