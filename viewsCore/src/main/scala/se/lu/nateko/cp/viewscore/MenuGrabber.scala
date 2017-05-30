package se.lu.nateko.cp.viewscore

import java.io.InputStream
import java.net.URL
import java.util.Scanner

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
		var res: String = ""
		val host: String = "https://www.icos-cp.eu/"

		val scanner = new Scanner(in)
		scanner.useDelimiter("<!--cp_menu-->")

		while(res == "" && scanner.hasNext){
			val block = scanner.next()
			if(block.contains("id=\"cp_theme_d8_menu\"")){	
				res = block.replaceAll("href=\"/", "href=\"" + host)
				res = res.replaceAll("src=\"/", "src=\"" + host)
			}
		}
		
		scanner.close()

		if(res.isEmpty) Failure(new Exception("Could not find menu in CP's main page HTML")) else Success(res)
	}
}
