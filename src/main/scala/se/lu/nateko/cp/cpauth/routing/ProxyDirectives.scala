package se.lu.nateko.cp.cpauth.routing

import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.HttpMessage
import akka.http.scaladsl.model.HttpProtocols
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteResult.Complete

trait ProxyDirectives { this: CpauthDirectives =>

	val http: HttpExt

	import ProxyDirectives._

	def proxyTo(host: Uri.Host, port: Int, path: Uri.Path, query: (String, String)*): Route = ctxt => {

		val req = ctxt.request
		val finalPath = if(path.isEmpty) Uri.Path./ else path
		val newQuery = mergeQueries(query, req.uri.query())
		val newUri = Uri./.withScheme("http").withHost(host).withPort(port).withPath(finalPath).withQuery(newQuery)
		val newReq = req.copy(uri = newUri, protocol = HttpProtocols.`HTTP/1.1`).withoutRedundantHeaders
		http.singleRequest(newReq).map(response => Complete(response.withoutRedundantHeaders))
	}

	def restheartProxy(baseUri: Uri): Route = ctxt => {
		val req = ctxt.request
		val newUri = req.uri.withScheme(baseUri.scheme).withAuthority(baseUri.authority)
		val newReq = req.copy(uri = newUri, protocol = HttpProtocols.`HTTP/1.1`).withoutRedundantHeaders
		http.singleRequest(newReq).map(response => Complete(response.withoutRedundantHeaders))
	}
}

object ProxyDirectives{

	import akka.http.scaladsl.model.headers._

	private val headersBlackList: Set[String] = {
		Set(`Content-Type`, `Content-Length`, Server, Date, `Transfer-Encoding`, `Timeout-Access`)
			.map(_.lowercaseName)
	}

	def mergeQueries(highPrio: Seq[(String, String)], old: Uri.Query) = Uri.Query{
		old.foldLeft(highPrio.toMap){
			case (map, pair @ (key, _)) => if(map.contains(key)) map else map + pair
		}
	}

	implicit class HeaderManipHttpMessage[T <: HttpMessage](val msg: T) extends AnyVal{

		def withoutRedundantHeaders: msg.Self = {
			val headers = msg.headers.filter(header => !headersBlackList.contains(header.lowercaseName))
			msg.withHeaders(headers)
		}

	}

}
