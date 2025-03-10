package ani.saikou.media

import android.app.Activity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import ani.saikou.anilist.Anilist
import ani.saikou.anime.Episode
import ani.saikou.anime.SelectorDialogFragment
import ani.saikou.anime.source.AnimeSources
import ani.saikou.kitsu.Kitsu
import ani.saikou.loadData
import ani.saikou.logger
import ani.saikou.manga.MangaChapter
import ani.saikou.manga.source.MangaSources
import ani.saikou.saveData
import kotlinx.coroutines.*

class MediaDetailsViewModel:ViewModel() {
    fun saveSelected(id:Int,data:Selected,activity: Activity){
        saveData("$id-select",data,activity)
    }
    fun loadSelected(id:Int):Selected{
        return loadData<Selected>("$id-select")?: Selected()
    }

    private val media: MutableLiveData<Media> = MutableLiveData<Media>(null)
    fun getMedia(): LiveData<Media> = media
    fun loadMedia(m:Media) { if (media.value==null) media.postValue(Anilist.query.mediaDetails(m)) }
    fun setMedia(m:Media) = media.postValue(m)

    val sources = MutableLiveData<ArrayList<Source>?>(null)

    val userScore = MutableLiveData<Double?>(null)
    val userProgress = MutableLiveData<Int?>(null)
    val userStatus = MutableLiveData<String?>(null)

    private val kitsuEpisodes: MutableLiveData<MutableMap<String,Episode>> = MutableLiveData<MutableMap<String,Episode>>(null)
    fun getKitsuEpisodes() : LiveData<MutableMap<String,Episode>> = kitsuEpisodes
    fun loadKitsuEpisodes(s:Media){ if (kitsuEpisodes.value==null) kitsuEpisodes.postValue(Kitsu.getKitsuEpisodesDetails(s))}

    private val episodes: MutableLiveData<MutableMap<Int,MutableMap<String,Episode>>> = MutableLiveData<MutableMap<Int,MutableMap<String,Episode>>>(null)
    private val epsLoaded = mutableMapOf<Int,MutableMap<String,Episode>>()
    fun getEpisodes() : LiveData<MutableMap<Int,MutableMap<String,Episode>>> = episodes
    fun loadEpisodes(media: Media,i:Int){
        logger("Loading Episodes : $epsLoaded")
        if(!epsLoaded.containsKey(i)) {
            epsLoaded[i] = AnimeSources[i]!!.getEpisodes(media)
        }
        episodes.postValue(epsLoaded)
    }
    fun overrideEpisodes(i: Int, source: Source,id:Int){
        AnimeSources[i]!!.saveSource(source,id)
        epsLoaded[i] = AnimeSources[i]!!.getSlugEpisodes(source.link)
        episodes.postValue(epsLoaded)
    }

    private var episode: MutableLiveData<Episode?> = MutableLiveData<Episode?>(null)
    fun getEpisode() : LiveData<Episode?> = episode
    fun loadEpisodeStreams(ep: Episode,i:Int){
        episode.postValue(AnimeSources[i]?.getStreams(ep)?:ep)
        MainScope().launch(Dispatchers.Main) {
            episode.value = null
        }
    }
    fun loadEpisodeStream(ep: Episode,selected: Selected):Boolean{
        return if(selected.stream!=null) {
            episode.postValue(AnimeSources[selected.source]?.getStream(ep, selected.stream!!))
            MainScope().launch(Dispatchers.Main) {
                episode.value = null
            }
            true
        } else false
    }
    fun setEpisode(ep: Episode?){
        println("Setting EP : ${ep?.number}")
        episode.postValue(ep)
        MainScope().launch(Dispatchers.Main) {
            episode.value = null
        }
    }

    val epChanged = MutableLiveData(true)
    fun onEpisodeClick(media: Media, i:String,manager:FragmentManager,launch:Boolean=true){
        if (media.anime?.episodes?.get(i)!=null)
            media.anime.selectedEpisode = i
        else {
            logger("Couldn't find episode : $i")
            return
        }
        media.selected = this.loadSelected(media.id)
        if(media.selected!!.stream!=null)
            SelectorDialogFragment.newInstance(media.selected!!.stream,launch).show(manager,"dialog")
        else
            SelectorDialogFragment.newInstance(la=launch).show(manager,"dialog")
    }

    private val mangaChapters: MutableLiveData<MutableMap<Int,MutableMap<String,MangaChapter>>> = MutableLiveData<MutableMap<Int,MutableMap<String,MangaChapter>>>(null)
    private val mangaLoaded = mutableMapOf<Int,MutableMap<String,MangaChapter>>()
    fun getMangaChapters() : LiveData<MutableMap<Int,MutableMap<String,MangaChapter>>> = mangaChapters
    fun loadMangaChapters(media:Media,i:Int){
        logger("Loading Manga Chapters : $mangaLoaded")
        if(!mangaLoaded.containsKey(i)){
            mangaLoaded[i] = MangaSources[i]!!.getChapters(media)
        }
        mangaChapters.postValue(mangaLoaded)
    }
    fun overrideMangaChapters(i: Int, source: Source,id:Int){
        MangaSources[i]!!.saveSource(source,id)
        mangaLoaded[i] = MangaSources[i]!!.getLinkChapters(source.link)
        mangaChapters.postValue(mangaLoaded)
    }
}