package com.anjunar.blog.frontend

import ui.core.state.Property

final class BlogActions(reloadPage: () => Unit) {
  val showSummaries: Property[Boolean] = Property(true)

  def toggleSummaries(): Unit = showSummaries.set(!showSummaries.get)

  // A retry starts a fresh document request at the same URL.
  def retry(): Unit = reloadPage()
}
