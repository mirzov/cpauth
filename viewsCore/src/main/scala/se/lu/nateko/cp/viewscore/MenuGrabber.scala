package se.lu.nateko.cp.viewscore

import java.io.{BufferedReader, InputStream, InputStreamReader}
import java.net.URL
import java.util.regex.{Pattern, Matcher}
import scala.util.{Failure, Success, Try}

object MenuGrabber {

	def getMenu: Try[String] = for(
		in <- getPageStream();
		html <- findMenu(in)
	) yield html

	private def getPageStream(): Try[InputStream] = Try{
		new URL(views.CpMenu.cpHome).openStream()
	}

	private def findMenu(in: InputStream): Try[String] = {
		var menuStructure: String = ""
		var inMenu:Boolean = false
		var co: Int = 0
		val startDivPattern = Pattern.compile("<div")
		val stopDivPattern = Pattern.compile("</div>")
		val host: String = views.CpMenu.cpHome + "/"

		val reader = new BufferedReader(new InputStreamReader(in))
		var line: String = null

		while({line = reader.readLine(); line != null}) {
			if(line.contains("id=\"cp_theme_d8_menu\"")){
				inMenu = true
			}

			if(inMenu){
				menuStructure += line

				val startDiv = startDivPattern.matcher(line)
				val stopDiv = stopDivPattern.matcher(line)

				while(startDiv.find()){
					co += 1
				}

				while(stopDiv.find()){
					co -= 1
				}
			}

			if(inMenu && co == 0){
				inMenu = false
			}
		}

		menuStructure = menuStructure.substring(menuStructure.indexOf("<div"), menuStructure.lastIndexOf("</div>") + 6)

		if(! menuStructure.isEmpty){
			menuStructure = menuStructure.replaceAll("href=\"/", "href=\"" + host)
			menuStructure = menuStructure.replaceAll("src=\"/", "src=\"" + host)
		}

		if(menuStructure.isEmpty) Failure(new Exception("Could not find menu in CP's main page HTML")) else Success(menuStructure)
	}
}
