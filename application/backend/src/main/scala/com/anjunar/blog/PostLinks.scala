package com.anjunar.blog

import com.anjunar.json.mapper.schema.Link
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject

import java.util
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

@RequestScoped
class PostLinks {
  @Inject var access: PostAccess = uninitialized
  @Inject var caller: CallerAccess = uninitialized

  private def endpoint(name: String): Boolean = {
    val parameters = if (name == "list") Array(classOf[String], classOf[String]) else Array(classOf[String])
    val method = classOf[EditorialPostsResource].getMethod(name, parameters*)
    EndpointPolicy.of(method, classOf[EditorialPostsResource]).allows(caller.hasRole)
  }

  def session(): util.List[Link] =
    if (endpoint("list")) util.List.of(new Link("editorial", "/service/editorial/posts", "GET", "BlogPost"))
    else util.List.of()

  def publicPost(post: BlogPost): util.List[Link] = {
    val links = new util.ArrayList[Link]()
    links.add(new Link("self", s"/service/blog/posts/${post.slug}", "GET", "BlogPost"))
    if (endpoint("read"))
      links.add(new Link("preview", s"/service/editorial/posts/${post.id}", "GET", "BlogPost"))
    links
  }

  def editorialPost(post: BlogPost): util.List[Link] = {
    val path = s"/service/editorial/posts/${post.id}"
    val links = new util.ArrayList[Link]()
    links.add(new Link("self", path, "GET", "BlogPost"))
    if (endpoint("publish") && access.canPublish(post))
      links.add(new Link("publish", s"$path/publish", "POST", "BlogPost"))
    if (endpoint("retract") && access.canRetract(post))
      links.add(new Link("retract", s"$path/retract", "POST", "BlogPost"))
    if (post.status == BlogPostStatus.PUBLISHED)
      links.add(new Link("public", s"/service/blog/posts/${post.slug}", "GET", "BlogPost"))
    links
  }

  def page(offset: Int, limit: Int, total: Long): util.List[Link] = {
    def link(rel: String, start: Int) =
      new Link(rel, s"/service/editorial/posts?offset=$start&limit=$limit", "GET", "BlogPost")
    val values = Seq(Some(link("self", offset)),
      Option.when(offset > 0)(link("previous", math.max(0, offset - limit))),
      Option.when(offset.toLong + limit < total && offset <= Int.MaxValue - limit)(link("next", offset + limit)))
    values.flatten.asJava
  }
}
