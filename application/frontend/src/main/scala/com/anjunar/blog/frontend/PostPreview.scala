package com.anjunar.blog.frontend

// A read-only preview for the first UI; the API model follows in chapter 10.
final case class PostPreview(
    id: String,
    slug: String,
    title: String,
    summary: String,
    publishedAt: String
)

object ExamplePosts {
  val all: Seq[PostPreview] = Seq(
    PostPreview(
      "b62db12a-61a7-46a5-b8d2-c487f80e825a",
      "serving-posts-through-rest",
      "Serving posts through REST",
      "Give readers a small, predictable API. Start with published posts, a useful list, and the full story behind each title.",
      "2026-09-27T10:15:42Z"
    ),
    PostPreview(
      "437e408e-c027-4edf-a5da-0d9156c47965",
      "one-model-two-jobs",
      "One model, two jobs",
      "Use the same field description for JSON mapping and typed database queries. Less duplication, with a contract you can test.",
      "2026-09-26T09:00:00Z"
    ),
    PostPreview(
      "35d433b8-7b67-437b-bfe3-389aa38e18f8",
      "room-for-the-model-to-grow",
      "Room for the model to grow",
      "A schema changes as an application takes shape. Add a summary, keep existing posts, and make the next migration repeatable.",
      "2026-09-25T08:00:00Z"
    )
  )
}
