package se.lu.nateko.cp.cpauth.routing

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import play.twirl.api.Html
import se.lu.nateko.cp.cpauth.utils.TemplatePageMarshalling
import se.lu.nateko.cp.cpauth.CpauthConfig
import se.lu.nateko.cp.cpauth.Envri.Envri

trait StaticRouting extends CpauthDirectives{

	def oauthConfig: CpauthConfig.OAuthConfig

	private def oauthInfoForLoginPage(envri: Envri): String =
		oauthConfig.get(envri).map(CpauthConfig.oauthJson).getOrElse("{}")

	private[this] val pages: PartialFunction[String, Option[Envri => Html]] = {
		case "login" => Some(envri => views.html.CpauthLoginPage(oauthInfoForLoginPage(envri)))
		case "home" => Some(_ => views.html.CpauthHomePage())
		case "administration" => Some(_ => views.html.CpauthAdminPage())
		case "passwordreset" => None
	}

	private[this] implicit val pageMarsh = TemplatePageMarshalling.marshaller

	lazy val staticRoute: Route =
		path("favicon.ico"){
			getFromResource("favicon.ico")
		} ~
//		path("home" ~ Slash){
//			complete(views.html.CpauthHomePage())
//		} ~
		pathPrefix("images"){
		  getFromResourceDirectory("www/images")
		} ~
		pathPrefix(Segment){pageId =>
			if(pages.isDefinedAt(pageId)) {
				(pathSingleSlash & extractEnvri){envri =>
					pages(pageId) match{
						case Some(pageFactory) => extractEnvri{envri =>
							complete(pageFactory(envri))
						}
						case None => reject
					}
				} ~
				pathEnd{
					redirect(s"/$pageId/", StatusCodes.Found)
				} ~
				path(s"$pageId.js"){
					getFromResource(s"www/$pageId.js")
				} ~
				path("common.js"){
					getFromResource(s"www/common.js")
				}
			} else reject
		}
}
