package com.anjunar.blog

import com.anjunar.json.mapper.provider.EntityProvider
import com.anjunar.json.mapper.schema.Link
import jakarta.ws.rs.{ApplicationPath, DELETE, GET, PATCH, POST, PUT, Path, PathParam, QueryParam}
import jakarta.ws.rs.core.UriBuilder

import java.lang.reflect.Method
import java.util
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import scala.quoted.*

// Adapted from Anjunar Stack's LinkBuilder for this application's endpoint policy and /service prefix.
class LinkBuilder(val href: String, var rel: String, val method: String, val function: Method) {

  var withId: Boolean = false
  val variables: util.Map[String, Any] = new util.HashMap[String, Any]()

  def withVariable(name: String, value: Any): LinkBuilder = {
    if (value != null) variables.put(name, value match {
      case entity: EntityProvider => entity.id
      case other => other
    })
    this
  }

  def withRel(rel: String): LinkBuilder = {
    this.rel = rel
    this
  }

  def withId(value: Boolean): LinkBuilder = {
    this.withId = value
    this
  }

  def build(): Link = {
    if (href == null) return null

    val actualRel =
      if (rel == null) {
        if (function != null) function.getName else "self"
      } else rel

    val linkId =
      if (!withId || function == null) null
      else function.getDeclaringClass.getSimpleName.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT).stripSuffix("-resource") + "-" + function.getName

    new Link(actualRel, resolveHref(), method, linkId)
  }

  private def resolveHref(): String = {
    val prefix = classOf[ServerApplication].getAnnotation(classOf[ApplicationPath]).value()
    UriBuilder.fromPath(prefix).path(href).resolveTemplates(variables).build().toString
  }
}

object LinkBuilder {

  private val methodCache = new ConcurrentHashMap[(String, String), Method]()

  def getMethod(className: String, methodName: String): Method =
    methodCache.computeIfAbsent((className, methodName), _ => {
      val matches = Class.forName(className).getMethods.filter(_.getName == methodName)
      require(matches.length == 1, s"Link endpoints must have a unique method name: $className.$methodName")
      matches.head
    })

  inline def create[C](inline call: C => Any): LinkBuilder =
    ${ createMacroImpl[C]('call) }

  private def createMacroImpl[C: Type](callExpr: Expr[C => Any])(using q: Quotes): Expr[LinkBuilder] = {
    import q.reflect.*

    val (methodSym, argExprs) = extractMethodCall(callExpr)
    val (httpMethod, hrefTemplate) = extractMappingAnnotation(methodSym)
    val paramBindings = extractParameters(methodSym, argExprs)
    val pathVariables = extractPathVariables(hrefTemplate)

    val methodName = methodSym.name
    val controllerClassName = TypeRepr.of[C].typeSymbol.fullName

    val boundNames = paramBindings.map(_._1).toSet
    val unboundPathVariables = pathVariables.filterNot(boundNames.contains)

    val extraBindings = unboundPathVariables.flatMap { varName =>
      paramBindings.collectFirst {
        case (_, expr) if hasField(expr.asTerm.tpe, varName) =>
          val fieldTerm = accessField(expr.asTerm, varName)
          (varName, fieldTerm)
      }
    }

    '{
      val javaMethod = getMethod(${ Expr(controllerClassName) }, ${ Expr(methodName) })

      val builder = checkRolesAndGenerate(javaMethod, ${ Expr(hrefTemplate) }, ${ Expr(httpMethod) })

      ${
        val paramVariableCalls = paramBindings.map { case (name, expr) =>
          '{ builder.withVariable(${ Expr(name) }, $expr) }
        }

        val extraVariableCalls = extraBindings.map { case (name, fieldTerm) =>
          '{ builder.withVariable(${ Expr(name) }, ${ fieldTerm.asExpr }) }
        }

        Expr.block(paramVariableCalls ++ extraVariableCalls, '{ builder })
      }
    }
  }

  private def extractPathVariables(template: String): List[String] =
    "\\{([^}]+)}".r.findAllMatchIn(template).map(_.group(1)).toList

  private def hasField(using q: Quotes)(tpe: q.reflect.TypeRepr, name: String): Boolean = {
    import q.reflect.*
    val sym = tpe.typeSymbol.fieldMember(name)
    if (sym.isNoSymbol) tpe.typeSymbol.methodMember(name).nonEmpty
    else true
  }

  private def accessField(using q: Quotes)(term: q.reflect.Term, name: String): q.reflect.Term = {
    import q.reflect.*
    val sym = term.tpe.typeSymbol.fieldMember(name)
    val finalSym = if (sym.isNoSymbol) term.tpe.typeSymbol.methodMember(name).head else sym
    Select(term, finalSym)
  }

  private def extractMethodCall(using q: Quotes)(expr: Expr[Any]): (q.reflect.Symbol, List[Expr[Any]]) = {
    import q.reflect.*

    def search(term: Term): (Symbol, List[Term]) = term match {
      case Inlined(_, _, inner) => search(inner)
      case Lambda(_, body) => search(body)
      case Block(Nil, last) => search(last)
      case Typed(inner, _) => search(inner)
      case Apply(fun, args) => (fun.symbol, args)
      case Select(_, _) => (term.symbol, Nil)
      case _ =>
        report.errorAndAbort(s"Unsupported link expression: ${term.show}. Use _.method(...).")
    }

    val (sym, args) = search(expr.asTerm)
    (sym, args.map(_.asExpr))
  }

  private def extractMappingAnnotation(using q: Quotes)(methodSym: q.reflect.Symbol): (String, String) = {
    import q.reflect.*

    val classPath = extractPathAnnotation(methodSym.owner)
    val methodPath = extractPathAnnotation(methodSym)

    val mapping = methodSym.annotations.collectFirst {
      case ann if ann.tpe <:< TypeRepr.of[GET] => ("GET", mergePath(classPath, methodPath))
      case ann if ann.tpe <:< TypeRepr.of[POST] => ("POST", mergePath(classPath, methodPath))
      case ann if ann.tpe <:< TypeRepr.of[PUT] => ("PUT", mergePath(classPath, methodPath))
      case ann if ann.tpe <:< TypeRepr.of[PATCH] => ("PATCH", mergePath(classPath, methodPath))
      case ann if ann.tpe <:< TypeRepr.of[DELETE] => ("DELETE", mergePath(classPath, methodPath))
    }

    mapping.getOrElse(report.errorAndAbort(s"Method '${methodSym.name}' has no supported JAX-RS HTTP annotation."))
  }

  private def extractPathAnnotation(using q: Quotes)(symbol: q.reflect.Symbol): String = {
    import q.reflect.*
    symbol.annotations.collectFirst {
      case ann if ann.tpe <:< TypeRepr.of[Path] => extractAnnotationValue(ann).getOrElse("")
    }.getOrElse("")
  }

  private def mergePath(classPath: String, methodPath: String): String = {
    val left = Option(classPath).getOrElse("").stripSuffix("/")
    val right = Option(methodPath).getOrElse("").stripPrefix("/")
    val combined =
      if (left.isEmpty) right
      else if (right.isEmpty) left
      else s"$left/$right"
    if (combined.startsWith("/")) combined else "/" + combined
  }

  private def extractParameters(using q: Quotes)(methodSym: q.reflect.Symbol, argExprs: List[Expr[Any]]): List[(String, Expr[Any])] = {
    import q.reflect.*
    val params = methodSym.paramSymss.flatten
    params.zip(argExprs).map { (param, expr) =>
      val name = param.annotations.collectFirst {
        case ann if ann.tpe <:< TypeRepr.of[PathParam] => extractAnnotationValue(ann)
        case ann if ann.tpe <:< TypeRepr.of[QueryParam] => extractAnnotationValue(ann)
      }.flatten.getOrElse(param.name)
      (name, expr)
    }
  }

  private def extractAnnotationValue(using q: Quotes)(ann: q.reflect.Term): Option[String] = {
    import q.reflect.*
    ann match {
      case Apply(_, args) => args.collectFirst {
        case NamedArg("value" | "name", Literal(StringConstant(v))) => v
        case Literal(StringConstant(v)) => v
      }
      case _ => None
    }
  }

  def checkRolesAndGenerate(javaMethod: Method, href: String, httpMethod: String): LinkBuilder = {
    if (javaMethod == null) return new LinkBuilder(null, null, null, null)

    val policy = EndpointPolicy.of(javaMethod, javaMethod.getDeclaringClass)
    val allowed = policy match {
      case EndpointPolicy.Public => true
      case EndpointPolicy.Denied => false
      case _ => policy.allows(RuntimeContext.bean(classOf[CallerAccess]).hasRole)
    }
    if (allowed) {
      new LinkBuilder(href, null, httpMethod, javaMethod)
    } else {
      new LinkBuilder(null, null, null, null)
    }
  }
}
