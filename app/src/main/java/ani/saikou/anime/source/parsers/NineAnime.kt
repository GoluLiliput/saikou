package ani.saikou.anime.source.parsers

import ani.saikou.*
import ani.saikou.anime.Episode
import ani.saikou.anime.source.AnimeParser
import ani.saikou.media.Media
import ani.saikou.media.Source
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup
import java.net.URLEncoder

class NineAnime(private val dub:Boolean=false, override val name: String = "9Anime Scraper - animekisa.in"): AnimeParser() {

    //WE DO A LIL TROLLIN
    private val host = listOf(
        "https://animekisa.in/"
        //where 9anime.to
    )

    override fun getStream(episode: Episode, server: String): Episode {
        val streams = mutableMapOf<String,Episode.StreamLinks?>()
        try{
        Jsoup.connect(episode.link!!).get().select("#servers-list ul.nav li a").forEach { servers ->
            val embedLink = servers.attr("data-embed") // embed link of servers
            val name = servers.select("span").text()
            if(name==server){
                val token = Regex("(?<=window.skey = )'.*?'").find(
                    Jsoup.connect(embedLink).header("referer", host[0]).get().html()
                )?.value?.trim('\'') //token to get the m3u8
                    val m3u8Link = Json.decodeFromString<JsonObject>(Jsoup.connect("${embedLink.replace("/e/", "/info/")}&skey=$token")
                        .header("referer", host[0])
                        .ignoreContentType(true).get().body().text())["media"]!!.jsonObject["sources"]!!.jsonArray[0].jsonObject["file"].toString().trim('"')
                    streams[name] = (Episode.StreamLinks(name,listOf(Episode.Quality(m3u8Link,"Multi Quality",null)),"https://vidstream.pro/"))
            }
        }
        }catch (e:Exception){ toastString(e.toString()) }
        episode.streamLinks = streams
        return episode
    }

    override fun getStreams(episode: Episode): Episode {
        val streams = mutableMapOf<String,Episode.StreamLinks?>()
        try{
        Jsoup.connect(episode.link!!).get().select("#servers-list ul.nav li a").forEach { servers ->
            val embedLink = servers.attr("data-embed") // embed link of servers
            val name = servers.select("span").text()
            val token = Regex("(?<=window.skey = )'.*?'").find(
                Jsoup.connect(embedLink).header("referer", host[0]).get().html()
            )?.value?.trim('\'') //token to get the m3u8

            val m3u8Link = Json.decodeFromString<JsonObject>(Jsoup.connect("${embedLink.replace("/e/", "/info/")}&skey=$token")
                .header("referer", host[0])
                .ignoreContentType(true).get().body().text())["media"]!!.jsonObject["sources"]!!.jsonArray[0].jsonObject["file"].toString().trim('"')
            streams[name] = (Episode.StreamLinks(name,listOf(Episode.Quality(m3u8Link,"Multi Quality",null)),"https://vidstream.pro/"))
        }
        }catch (e:Exception){ toastString(e.toString()) }
        episode.streamLinks = streams
        return episode
    }

    override fun getEpisodes(media: Media): MutableMap<String, Episode> {
        var slug:Source? = loadData("animekisa_in${if(dub) "dub" else ""}_${media.id}")
        if (slug==null) {
            val it = media.nameMAL?:media.name
            live.postValue("Searching for $it")
            logger("9anime : Searching for $it")
            val search = search("$! | &language%5B%5D=${if(dub) "d" else "s"}ubbed&year%5B%5D=${media.anime?.seasonYear}&sort=default&season%5B%5D=${media.anime?.season?.lowercase()}&type%5B%5D=${media.typeMAL?.lowercase()}")
            if (search.isNotEmpty()) {
                search.sortByTitle(it)
                slug = search[0]
                saveSource(slug,media.id,false)
            }
        }
        else{
            live.postValue("Selected : ${slug.name}")
        }
        if (slug!=null) return getSlugEpisodes(slug.link)
        return mutableMapOf()
    }

    override fun search(string: String): ArrayList<Source> {
        //THIS IS LIKE THE WORST SEARCH ENGINE OF A WEBSITE
        var url = URLEncoder.encode(string, "utf-8")
        if(string.startsWith("$!")){
            val a = string.replace("$!","").split(" | ")
            url = URLEncoder.encode(a[0], "utf-8")+a[1]
        }
        val responseArray = arrayListOf<Source>()
        Jsoup.connect("${host[0]}filter?keyword=$url").get()
            .select("#main-wrapper .film_list-wrap > .flw-item .film-poster").forEach{
                val link = it.select("a").attr("href")
                val title = it.select("img").attr("title")
                val cover = it.select("img").attr("data-src")
                responseArray.add(Source(link,title,cover))
            }
        return responseArray
    }

    override fun getSlugEpisodes(slug:String): MutableMap<String, Episode>{
        val responseArray = mutableMapOf<String,Episode>()
        try{
        val pageBody = Jsoup.connect(slug).get().body()
        pageBody.select(".tab-pane > ul.nav").forEach{
            it.select("li>a").forEach { i ->
                val num = i.text().trim()
                responseArray[num] = Episode(number = num,link = i.attr("href").trim())
            }
        }
        logger("Response Episodes : $responseArray")
        }catch (e:Exception){ toastString(e.toString()) }
        return responseArray
    }

    override fun saveSource(source: Source, id: Int, selected: Boolean) {
        super.saveSource(source, id, selected)
        saveData("animekisa_in${if(dub) "dub" else ""}_$id", source)
    }
}