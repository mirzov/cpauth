package se.lu.nateko.cp.cpauth.opensaml

import java.net.URI
import java.net.URL
import java.security.PublicKey
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import org.opensaml.common.xml.SAMLConstants
import org.opensaml.saml2.metadata.EntitiesDescriptor
import org.opensaml.saml2.metadata.EntityDescriptor
import org.opensaml.saml2.metadata.IDPSSODescriptor
import org.opensaml.saml2.metadata.provider.MetadataProviderException
import org.opensaml.samlext.saml2mdui.UIInfo
import se.lu.nateko.cp.cpauth.Utils.SafeJavaCollectionWrapper
import se.lu.nateko.cp.cpauth.core.SamlConfig
import se.lu.nateko.cp.cpauth.core.Crypto
import se.lu.nateko.cp.cpauth.Utils

case class IdpInfo(name: String, id: String)

class IdpProps(val name: String, val key: PublicKey, val ssoRedirect: URL)

trait IdpLibrary{

	protected val map: Map[URI, IdpProps]

	def getIdpProps(idpId: URI): Try[IdpProps] = map.get(idpId) match {
		case None => Failure(new MetadataProviderException("Unknown Identity Provider: " + idpId))
		case Some(key) => Success(key)
	}

	def getInfos: Iterable[IdpInfo] = map.map{
		case (id, props) => IdpInfo(props.name, id.toString)
	}

}

object IdpLibrary {

	OpenSamlUtils.bootstrapOpenSaml()
	Utils.setRootLoggingLevelToInfo()


	def fromConfig(config: SamlConfig): IdpLibrary = {
		val idpMetaStream = getClass.getResourceAsStream(config.idpMetadataFilePath)
		fromMetaStream(idpMetaStream)
	}

	def fromMetaStream(metadata: java.io.InputStream): IdpLibrary = {

		val entsDescr = Parser.fromStream[EntitiesDescriptor](metadata)
		val entDescrs = entsDescr.getEntityDescriptors.toSafeIterable

		val urisToProps: Iterable[(URI, IdpProps)] = entDescrs.map(ed => {

			val idp = ed.getIDPSSODescriptor(SAMLConstants.SAML20P_NS)

			for{
				id <- getId(ed);
				key <- idpToPublicKey(idp);
				redirect <- idpToSsoRedirect(idp);
				name <- idpToName(idp, id.toString)
			} yield (id, new IdpProps(name, key, redirect))

		}).collect{
			case (Success(uriToProps)) => uriToProps
		}

		new IdpLibrary{
			override val map = Map(urisToProps.toSeq: _*)
		}
	}

	private def idpToPublicKey(idp: IDPSSODescriptor): Try[PublicKey] = for(
		cert <- idpToCertInBase64(idp);
		key <- Crypto.publicKeyFromX509Cert(cert)
	) yield key
	
	private def idpToCertInBase64(idp: IDPSSODescriptor): Try[String] = Try(
		idp.getKeyDescriptors.get(0).getKeyInfo.getX509Datas.get(0).getX509Certificates.get(0).getValue
	)
	
	private def idpToSsoRedirect(idp: IDPSSODescriptor): Try[URL] = Try{
		val redirectSss = idp.getSingleSignOnServices.toSafeIterable.find{
			_.getBinding == SAMLConstants.SAML2_REDIRECT_BINDING_URI
		}
		redirectSss match {
			case None => throw new MetadataProviderException("HTTP-Redirect binding is not supported")
			case Some(sss) => new URL(sss.getLocation)
		}
	}

	private def idpToName(idp: IDPSSODescriptor, fallback: String): Try[String] = Try{
		idp.getExtensions.getOrderedChildren.toSafeIterable.collect{
			case ui: UIInfo => getDisplayName(ui)
		}.flatten.headOption.getOrElse(fallback)
	}
	
	private def getDisplayName(ui: UIInfo): Option[String] = {
		val names = ui.getDisplayNames.toSafeIterable.map(_.getName).toIndexedSeq
		names.find(_.getLanguage == "en").orElse(names.headOption).map(_.getLocalString)
	}

	private def getId(ed: EntityDescriptor): Try[URI] = Try{
		new URI(ed.getEntityID)
	}

}