package se.lu.nateko.cp.cpauth.routing

import akka.http.scaladsl.model.{HttpMethods, StatusCodes}
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.cpauth.Envri.Envri

trait RestHeartRouting extends RestHeartDirectives{

	val restheartRoute: Route = extractEnvri{implicit envri =>
		val config = restHeart.config

		path(config.dbName / config.usersCollection / Segment){ email =>
			options{
				echoOriginToAllowOrigin(envri){
					respondWithHeaders(
						`Access-Control-Allow-Methods`(HttpMethods.GET, HttpMethods.POST, HttpMethods.PUT, HttpMethods.PATCH),
						`Access-Control-Allow-Credentials`(true),
						`Access-Control-Allow-Headers`("Content-Type")
					){
						complete(StatusCodes.OK)
					}
				}
			} ~ {
				token { token =>
					(validateUser(email, token.userId) | ifUserIsAdmin(token)) {
						echoOriginToAllowOrigin(envri){
							restheartProxy
						}
					} ~
					forbid("Access to other users' documents is forbidden")
				} ~
				forbid("Must be logged in with Carbon Portal for this operation")
			}
		}
	}

	private def validateUser(email: String, uid: UserId): Directive0 =
		validate(email == uid.email, "Only admins can write to other users' documents")

	def echoOriginToAllowOrigin(implicit envri: Envri): Directive0 = headerValueByType[Origin](()).flatMap{origin =>
		if(origin.value.endsWith(publicAuthConfigs(envri).authCookieDomain))
			respondWithHeader(`Access-Control-Allow-Origin`(origin.value))
		else pass
	}.recover(_ => pass)

}
