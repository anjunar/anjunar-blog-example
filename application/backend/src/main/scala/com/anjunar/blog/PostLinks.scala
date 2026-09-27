package com.anjunar.blog

import com.anjunar.json.mapper.PreparedChange
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
    val parameters: Seq[Class[?]] = name match {
      case "list" => Seq(classOf[PostSearchParams])
      case "create" | "update" => Seq(classOf[PreparedChange[?]])
      case _ => Seq(classOf[String])
    }
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
    if (endpoint("update") && access.canEdit(post))
      links.add(new Link("update", path, "PATCH", "BlogPost"))
    if (endpoint("publish") && access.canPublish(post))
      links.add(new Link("publish", s"$path/publish", "POST", "BlogPost"))
    if (endpoint("retract") && access.canRetract(post))
      links.add(new Link("retract", s"$path/retract", "POST", "BlogPost"))
    if (post.status == BlogPostStatus.PUBLISHED)
      links.add(new Link("public", s"/service/blog/posts/${post.slug}", "GET", "BlogPost"))
    links
  }

  def summary(post: BlogPostSummary, editorial: Boolean): util.List[Link] = {
    val path = if (editorial) s"/service/editorial/posts/${post.id}"
      else s"/service/blog/posts/${post.slug}"
    val values = new util.ArrayList[Link]()
    values.add(new Link("self", path, "GET", "BlogPost"))
    if (editorial && endpoint("update"))
      values.add(new Link("update", path, "PATCH", "BlogPost"))
    if (!editorial && endpoint("read"))
      values.add(new Link("preview", s"/service/editorial/posts/${post.id}", "GET", "BlogPost"))
    // Publication capabilities need the loaded body and are advertised by detail responses.
    values
  }

  def page(search: PostSearch, total: Long, editorial: Boolean): util.List[Link] = {
    val path = if (editorial) "/service/editorial/posts" else "/service/blog/posts"
    def link(rel: String, start: Int) =
      new Link(rel, search.pageUrl(path, start), "GET", "BlogPost")
    val values = Seq(Some(link("self", search.offset)),
      Option.when(editorial && endpoint("create"))(new Link("create", path, "POST", "BlogPost")),
      Option.when(search.offset > 0)(link("first", 0)),
      Option.when(search.offset > 0)(link("previous", math.max(0, search.offset - search.limit))),
      Option.when(search.offset.toLong + search.limit < total && search.offset <= Int.MaxValue - search.limit)(
        link("next", search.offset + search.limit)))
    values.flatten.asJava
  }
}
