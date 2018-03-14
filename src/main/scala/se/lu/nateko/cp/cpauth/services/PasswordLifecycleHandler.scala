package se.lu.nateko.cp.cpauth.services

import scala.concurrent.Future
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.cpauth.core.AuthSource
import scala.concurrent.ExecutionContext
import se.lu.nateko.cp.cpauth.HttpConfig
import java.net.URI
import se.lu.nateko.cp.cpauth.accounts.UsersIo
import se.lu.nateko.cp.cpauth.accounts.UserEntry
import se.lu.nateko.cp.cpauth.utils.Utils
import scala.concurrent.duration.DurationInt
import akka.actor.Scheduler
import se.lu.nateko.cp.cpauth.Envri.Envri

class PasswordLifecycleHandler(
	emailSender: EmailSender,
	cookieFactory: CookieFactory,
	userDb: UsersIo,
	config: HttpConfig
)(implicit ctxt: ExecutionContext, scheduler: Scheduler) {

	def sendResetEmail(uid: UserId)(implicit envri: Envri): Future[Unit] = {

		val tokenTry = cookieFactory.makeTokenBase64(uid, AuthSource.PasswordReset)

		Future.fromTry(tokenTry).map(token => {
			val link = new URI("https", config.serviceHost, "/password/initpassreset/" + token, null)
			val message = views.html.CpauthPassResetEmail(uid.email, link)
			emailSender.send(Seq(uid.email), "Create/reset you Carbon Portal password", message)
		})
	}

	def setPassword(uid: UserId, newPassword: String): Future[Unit] = {
		userDb.userExists(uid).flatMap(exists =>
			if(exists)
				userDb.userIsAdmin(uid).flatMap(isAdmin =>
					userDb.updateUser(uid, UserEntry(uid, isAdmin), newPassword)
				)
			else
				userDb.addUser(UserEntry(uid, false), newPassword)
		)
	}

	def changePassword(uid: UserId, oldPassword: String, newPassword: String): Future[Unit] = for(
		userEntry <- authUser(uid, oldPassword);
		_ <- userDb.updateUser(uid, userEntry, newPassword)
	) yield ()

	def authUser(uid: UserId, password: String): Future[UserEntry] =
		Utils.slowFailureDown(userDb.authenticateUser(uid, password), 500.millis)
}
