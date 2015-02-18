package se.lu.nateko.cpauth.opensaml

import java.security.interfaces.RSAPrivateKey

import scala.util.Try

import org.opensaml.saml2.core.Assertion
import org.opensaml.saml2.core.EncryptedAssertion
import org.opensaml.saml2.core.Response
import org.opensaml.saml2.encryption.Decrypter
import org.opensaml.xml.encryption.InlineEncryptedKeyResolver
import org.opensaml.xml.schema.XSString
import org.opensaml.xml.security.keyinfo.StaticKeyInfoCredentialResolver
import org.opensaml.xml.security.x509.BasicX509Credential

import se.lu.nateko.cpauth.Utils.SafeJavaCollectionWrapper
import se.lu.nateko.cpauth.core.Config
import se.lu.nateko.cpauth.core.CoreUtils
import se.lu.nateko.cpauth.core.Crypto

class AssertionExtractor(key: RSAPrivateKey){
	import AssertionExtractor._

	lazy val decrypter: AssertionDecrypter = {

		val decryptionCredential = new BasicX509Credential()
		decryptionCredential.setPrivateKey(key)

		val decrypter = new Decrypter(null, new StaticKeyInfoCredentialResolver(decryptionCredential), new InlineEncryptedKeyResolver())

		decrypter.decrypt
	}

	def extractAssertions(response: Response): Iterable[Assertion] = {
		val decryptedAssertions = response.getEncryptedAssertions.toSafeIterable.map(decrypter)
		val unencryptedAssertions = response.getAssertions.toSafeIterable
		unencryptedAssertions ++ decryptedAssertions
	}

}

object AssertionExtractor {

	type AssertionDecrypter = EncryptedAssertion => Assertion

	OpenSamlUtils.bootstrapOpenSaml()

	def apply(conf: Config): Try[AssertionExtractor] = fromPrivateKeyAt(conf.privateKeyPath)

	def fromPrivateKeyAt(path: String): Try[AssertionExtractor] = {
		val keyBytes = CoreUtils.getResourceBytes(path)
		val privateKey = Crypto.rsaPrivateFromDerBytes(keyBytes)
		privateKey.map(key => new AssertionExtractor(key))
	}

}