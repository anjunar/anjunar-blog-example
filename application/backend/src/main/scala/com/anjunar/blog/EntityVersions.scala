package com.anjunar.blog

import com.anjunar.json.mapper.intermediate.model.{JsonNumber, JsonObject}

object EntityVersions {
  val conflictDetail = "The post changed. Reload it before saving again."

  def requireCurrent(post: BlogPost, json: JsonObject): Unit = {
    val version = json.value.get("version") match {
      case number: JsonNumber => number.value.toLongOption.filter(_ >= 0)
      case _ => None
    }
    if (version.isEmpty) Problem.invalidField("version", "Send the nonnegative integer version you loaded.")
    if (version.get != post.version) throw new ApiProblem(409, conflictDetail, Problem.conflict)
  }
}
