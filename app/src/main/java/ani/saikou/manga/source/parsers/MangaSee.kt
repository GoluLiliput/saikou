package ani.saikou.manga.source.parsers

import ani.saikou.*
import ani.saikou.manga.MangaChapter
import ani.saikou.manga.source.MangaParser
import ani.saikou.media.Media
import ani.saikou.media.Source
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import org.jsoup.Jsoup

class MangaSee(override val name: String="MangaSee") : MangaParser() {
    private val host = "https://mangasee123.com"

    override fun getLinkChapters(link: String): MutableMap<String, MangaChapter> {
        val responseArray = mutableMapOf<String, MangaChapter>()
        try{
        val a = Jsoup.connect("$host/manga/$link").maxBodySize(0).get().select("script").lastOrNull()
        val json = (a?:return responseArray).toString().findBetween("vm.Chapters = ",";")?:return responseArray

        Json.decodeFromString<JsonArray>(json).reversed().forEach {
            val chap = it.jsonObject["Chapter"].toString().trim('"')
            val name = if(it.jsonObject["ChapterName"]!=JsonNull) it.jsonObject["ChapterName"].toString().trim('"') else null
            val num = chapChop(chap,3)
            responseArray[num] = MangaChapter(num,name,host+"/read-online/"+ link + "-chapter-" + chapChop(chap,1) + chapChop(chap,2) + chapChop(chap,0) + ".html")
        }}catch (e:Exception){
            toastString(e.toString())
        }
        return responseArray
    }

    private fun chapChop (id:String, type:Int) : String = when (type){
        0 -> if (id.startsWith("1")) "" else ("-index-${id[0]}")
        1 -> (id.substring(1,5).replace("[^0-9]".toRegex(),""))
        2 -> if (id.endsWith("0")) "" else (".${id[id.length-1]}")
        3-> "${id.drop(1).dropLast(1).toInt()}${chapChop(id, 2)}"
        else -> ""
    }

    override fun getChapter(chapter: MangaChapter): MangaChapter {
        chapter.images = arrayListOf()
        println(chapter)
        try {
            val a =
                Jsoup.connect(chapter.link ?: return chapter).maxBodySize(0).get().select("script")
                    .lastOrNull()
            val str = (a ?: return chapter).toString()
            val server = (str.findBetween("vm.CurPathName = ", ";") ?: return chapter).trim('"')
            val slug = (str.findBetween("vm.IndexName = ", ";") ?: return chapter).trim('"')
            val chapJson = Json.decodeFromString<JsonObject>(
                str.findBetween("vm.CurChapter = ", ";") ?: return chapter
            )
            val id = chapJson["Chapter"].toString().trim('"')
            val chap = chapChop(id, 1) + chapChop(id, 2) + chapChop(id, 0)
            val pages = chapJson["Page"].toString().trim('"').toIntOrNull() ?: return chapter
            for (i in 1..pages)
                chapter.images!!.add("https://$server/manga/$slug/$chap-${"000$i".takeLast(3)}.png")
        }catch (e:Exception){
            toastString(e.toString())
        }
        return chapter
    }

    override fun getChapters(media: Media): MutableMap<String, MangaChapter> {
        var source:Source? = loadData("mangasee_${media.id}")
        if (source==null) {
            live.postValue("Searching : ${media.getMainName()}")
            val search = search(media.getMainName())
            if (search.isNotEmpty()) {
                logger("MangaSee : ${search[0]}")
                source = search[0]
                live.postValue("Found : ${source.name}")
                saveSource(source,media.id)
            }
        }
        else{
            live.postValue("Selected : ${source.name}")
        }
        if (source!=null) return getLinkChapters(source.link)
        return mutableMapOf()
    }

    override fun search(string: String): ArrayList<Source> {
        val response = arrayListOf<Source>()
        try{
            val a = Jsoup.connect("$host/search/").maxBodySize(0).get().select("script").lastOrNull()
            val json = (a?:return response).toString().findBetween("vm.Directory = ","\n")?.replace(";","")?:return response
            Json.decodeFromString<JsonArray>(json).forEach {
                response.add(Source(
                 name = it.jsonObject["s"].toString().trim('"'),
                 link = it.jsonObject["i"].toString().trim('"'),
                 cover = "https://cover.nep.li/cover/${it.jsonObject["i"].toString().trim('"')}.jpg"
                ))
            }
            response.sortByTitle(string)
        } catch(e:Exception){ toastString(e.toString()) }
        return response
    }

    override fun saveSource(source: Source, id: Int, selected: Boolean) {
        live.postValue("${if(selected) "Selected" else "Found"} : ${source.name}")
        saveData("mangasee_$id", source)
    }
}