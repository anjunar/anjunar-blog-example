package com.anjunar.blog

import com.anjunar.json.mapper.annotations.UseConverter
import com.anjunar.json.mapper.provider.DTO
import jakarta.json.bind.annotation.JsonbProperty

import java.time.Instant
import java.util.UUID
import scala.annotation.meta.field

// A read-only list projection. Load BlogPost detail before editing.
final class BlogPostSummary(
    @(JsonbProperty @field) val id: UUID,
    @(JsonbProperty @field) val version: Long,
    @(JsonbProperty @field) val slug: String,
    @(JsonbProperty @field) val title: String,
    @(JsonbProperty @field) val summary: String,
    @(JsonbProperty @field) val status: BlogPostStatus,
    @(JsonbProperty @field) @(UseConverter @field)(classOf[InstantConverter]) val publishedAt: Instant
) extends DTO
