package com.anjunar.blog

import jakarta.persistence.{Access, AccessType, Column, Entity, Id, Table}

import java.util.UUID

// A second entity mapping the existing table keeps the discovery test self-contained.
@Entity
@Access(AccessType.FIELD)
@Table(name = "blog_post", schema = "public")
class EntityDiscoveryProbe {
  @Id
  var id: UUID = null

  @Column(nullable = false, length = 180)
  var title: String = ""
}