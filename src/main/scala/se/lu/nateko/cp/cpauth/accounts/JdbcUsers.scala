package se.lu.nateko.cp.cpauth.accounts

import java.sql.Connection

import java.security.MessageDigest
import java.util.Base64

import scala.collection.mutable.Buffer
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.cpauth.core.AuthenticationFailedException


class JdbcUsers(getConnection: () => Connection)
			   (implicit ctxt: ExecutionContext) extends UsersIo {

	private def execute(statement: String): Future[Unit] = withConnection{ conn =>
		val st = conn.createStatement
		st.execute(statement)
		st.close()
	}

	private def withConnection[T](work: Connection => T): Future[T] = Future{
		val conn = getConnection()
		try{
			work(conn)
		} finally{
			conn.close()
		}
	}

	def hash(mail: String, pass: String): String = {
		val md = MessageDigest.getInstance("MD5")
		md.update(mail.getBytes("UTF-8"))
		val salt: Array[Byte] = md.digest
		val hmd = MessageDigest.getInstance("SHA-256")
		hmd.update( salt ++ pass.getBytes("UTF-8"))
		val hashBytes = hmd.digest
		Base64.getEncoder.encodeToString(hashBytes)
	}

	Await.ready(createTable(), Duration.Inf)
	Await.ready(dropColumnIfExists("GIVENNAME"), Duration.Inf)
	Await.ready(dropColumnIfExists("SURNAME"), Duration.Inf)

	def dropColumnIfExists(col: String): Future[Unit] =
		execute(s"""ALTER TABLE "USERS" DROP COLUMN "$col"""")

	def createTable(): Future[Unit] = {
		val sql = """create table if not exists "USERS" (
			"USER_ID" INTEGER GENERATED BY DEFAULT AS IDENTITY(START WITH 1) NOT NULL,
			"GIVENNAME" VARCHAR(254) NOT NULL,
			"SURNAME" VARCHAR(254) NOT NULL,
			"MAIL" VARCHAR(254) NOT NULL PRIMARY KEY,
			"PASSWORD" VARCHAR(254) NOT NULL,
			"ISADMIN" BOOLEAN DEFAULT false NOT NULL)"""
		execute(sql)
	}

	def addUser(userEntry: UserEntry, password: String): Future[Unit] = withConnection{ conn =>
		val ps = conn.prepareStatement("insert into users (mail,password,isadmin) values(?,?,?)")

		ps.setString(1, userEntry.id.email)
		ps.setString(2, hash(userEntry.id.email, password))
		ps.setBoolean(3, userEntry.isAdmin)
		ps.execute()
	}

	def userExists(uid: UserId): Future[Boolean] = withConnection { conn =>
		val ps = conn.prepareStatement("select 1 from users where mail = ?")
		ps.setString(1, uid.email)
		ps.executeQuery().next()
	}

	def authenticateUser(uid: UserId, password: String): Future[UserEntry] = withConnection { conn =>
		val ps = conn.prepareStatement("select isAdmin from users where mail = ? and password = ?")
		ps.setString(1, uid.email)
		ps.setString(2, hash(uid.email, password))

		val rs = ps.executeQuery()
		if (rs.next()) {
			UserEntry(uid, rs.getBoolean("isAdmin"))
		}
		else {
			throw AuthenticationFailedException
		}
	}

	def updateUser(oldUid: UserId, ue: UserEntry, newPass: String):Future[Unit] = withConnection { conn =>
		val ps = conn.prepareStatement("update users set isAdmin=?, password=? where mail=?")
		ps.setBoolean(1, ue.isAdmin)
		ps.setString(2, hash(ue.id.email, newPass))
		ps.setString(3, oldUid.email)
		ps.executeUpdate()
		}

	def dropUser(uid: UserId): Future[Unit] = withConnection { conn =>
		val ps = conn.prepareStatement("delete from users where mail = ?")
		ps.setString(1, uid.email)
		ps.executeUpdate()
	}

	def listUsers: Future[Seq[UserEntry]] = withConnection{conn =>
		val st = conn.createStatement
		val rs = st.executeQuery("""SELECT "MAIL", "ISADMIN" FROM "USERS"""")

		val result = Buffer.empty[UserEntry]

		while(rs.next()){
			result += UserEntry(UserId(rs.getString("MAIL")), rs.getBoolean("ISADMIN"))
		}
		result
	}

	def userIsAdmin(uid: UserId) = withConnection { conn =>
		val ps = conn.prepareStatement("select 1 from users where mail = ? and isAdmin")
		ps.setString(1, uid.email)
		ps.executeQuery().next()
	}

	def setAdminRights(uid: UserId, isAdmin: Boolean): Future[Unit] = withConnection { conn =>
		val ps = conn.prepareStatement("update users set isAdmin = ? where mail = ?")
		ps.setBoolean(1, isAdmin)
		ps.setString(2, uid.email)
		ps.executeUpdate()
	}


}