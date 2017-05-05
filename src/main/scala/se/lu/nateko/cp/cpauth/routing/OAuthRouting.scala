package se.lu.nateko.cp.cpauth.routing

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import se.lu.nateko.cp.cpauth.accounts.RestHeartClient
import se.lu.nateko.cp.cpauth.core.AuthSource
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.cpauth.oauth.FacebookAuthenticationService
import se.lu.nateko.cp.cpauth.oauth.OrcidAuthenticationService
import se.lu.nateko.cp.cpauth.services.CookieFactory

trait OAuthRouting {

	def facebookAuth: FacebookAuthenticationService
	def cookieFactory: CookieFactory
	implicit def dispatcher: ExecutionContext
	def restHeart: RestHeartClient
	def orcidIdAuthenticationService: OrcidAuthenticationService

	def facebookRoute: Route = pathPrefix("oauth" / "facebook"){
		oauthRoute(cpauthTokenFromFacebook, AuthSource.Facebook)
	}

	def orcidRoute: Route = pathPrefix("oauth" / "orcidid"){
		oauthRoute(cpauthTokenFromOrcidId, AuthSource.Orcid)
	}

	private def cpauthTokenFromFacebook(code: String): Future[UserId] = {
		facebookAuth.retrieveUserInfo(code).map{userInfo =>
			val uid = UserId(userInfo.email)

			//Silent side effect: creating user profile if it does not already exist
			restHeart.createUserIfNew(uid, userInfo.givenName, userInfo.surname)

			uid
		}
	}

	private def cpauthTokenFromOrcidId(code: String): Future[UserId] = {
		orcidIdAuthenticationService.retrieveUserInfo(code)
			.flatMap(userInfo => userInfo.email match {
				case Some(email) =>
					val uid = UserId(email)

					//Silent side effect: creating user profile if it does not already exist
					restHeart.createUserIfNew(uid, userInfo.givenName.getOrElse(""), userInfo.surname.getOrElse(""))

					Future.successful(uid)
				case None =>
					restHeart.findUsers(Map("profile.orcid" -> userInfo.orcidId))
						.map(_.headOption.getOrElse(throw new Exception(
							"You need to either make your (verified!) email public in your OrcidID account, " +
							"or log in to CP by other means first, and specify your OrcidId in your CP user profile"
						)))
			})
	}

	private def oauthRoute(uidProvider: String => Future[UserId], source: AuthSource.AuthSource): Route = {
		parameters('code, 'state ?){(code, targetUrl) =>
			val tokenFut: Future[String] = uidProvider(code).flatMap{uid =>
				Future.fromTry(
					cookieFactory.makeTokenBase64(uid, source)
				)
			}
			onSuccess(tokenFut){token =>

				setCookie(cookieFactory.makeAuthCookie(token)){

					targetUrl match{
						case Some(target) =>
							//getting rid of Facebook's appended #_=_
							val uri = if(Uri(target).fragment.isDefined) target else target + "#"
							redirect(uri, StatusCodes.Found)

						case None => redirect("/#", StatusCodes.Found)
					}
				}
			}
		}
	}

}
