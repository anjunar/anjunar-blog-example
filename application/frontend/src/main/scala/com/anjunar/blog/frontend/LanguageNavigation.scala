package com.anjunar.blog.frontend

import ui.core.component.AbstractComponent
import ui.core.di.Context
import ui.core.state.{Disposable, Property, ReadOnlyProperty}

import scala.collection.mutable

// Locale navigation replaces the routed page. A form owns its guard for exactly its lifetime.
final class LanguageNavigation {
  private val active = mutable.Set.empty[Object]
  private val blockedValue = Property(false)
  val blocked: ReadOnlyProperty[Boolean] = blockedValue

  def watch(states: Seq[ReadOnlyProperty[?]])(condition: => Boolean): Disposable = {
    val key = new Object()
    def update(): Unit = {
      if (condition) active.add(key) else active.remove(key)
      blockedValue.set(active.nonEmpty)
    }
    val subscriptions = states.map(_.observeWithoutInitial(_ => update()))
    update()
    Disposable {
      subscriptions.foreach(_.dispose())
      active.remove(key)
      blockedValue.set(active.nonEmpty)
    }
  }
}

object LanguageNavigation {
  private val context = Context.create[LanguageNavigation]("LanguageNavigation")

  def provide(value: LanguageNavigation)(using AbstractComponent): Unit = context.provide(value)

  def protect(states: ReadOnlyProperty[?]*)(condition: => Boolean)(using owner: AbstractComponent): Unit =
    context.inject.foreach(value => owner.addDisposable(value.watch(states)(condition)))
}
