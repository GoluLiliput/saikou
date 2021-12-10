package ani.saikou.manga

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import ani.saikou.R
import ani.saikou.databinding.FragmentMangaSourceBinding
import ani.saikou.media.Media
import ani.saikou.media.MediaDetailsViewModel
import ani.saikou.navBarHeight
import ani.saikou.saveData
import com.google.android.material.chip.Chip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.ceil

@SuppressLint("SetTextI18n")
class MangaSourceFragment : Fragment() {
    private var _binding: FragmentMangaSourceBinding? = null
    private val binding get() = _binding!!
    private val scope = CoroutineScope(Dispatchers.Default)
    private var screenWidth:Float =0f
    private var timer: CountDownTimer?=null

    private var selected: ImageView?=null
    private var selectedChip: Chip?= null
    private var start = 0
    private var end:Int?=null
    private var loading = true
    private var progress = View.VISIBLE
    private lateinit var model : MediaDetailsViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMangaSourceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() { super.onDestroyView();_binding = null }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        screenWidth = resources.displayMetrics.run { widthPixels / density }
        binding.mangaSourceContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin += navBarHeight }
        binding.mangaSourceTitle.isSelected = true
        super.onViewCreated(view, savedInstanceState)
        val a : MediaDetailsViewModel by activityViewModels()
        model = a
        model.parserText.observe(viewLifecycleOwner,{
            binding.mangaSourceTitle.text = it
        })

        model.getMedia().observe(viewLifecycleOwner,{
            val media = it
            if (media?.manga != null) {
//                if (media.manga.nextAiringChapterTime!=null && (media.manga.nextAiringChapterTime!!-System.currentTimeMillis()/1000)<=86400*7.toLong()) {
//                    binding.mangaSourceCountdownContainer.visibility = View.VISIBLE
//                    timer = object :
//                        CountDownTimer((media.manga.nextAiringChapterTime!! + 10000)*1000-System.currentTimeMillis(), 1000) {
//                        override fun onTick(millisUntilFinished: Long) {
//                            val a = millisUntilFinished/1000
//                            binding.mangaSourceCountdown.text = "Next Chapter will be released in \n ${a/86400} days ${a%86400/3600} hrs ${a%86400%3600/60} mins ${a%86400%3600%60} secs"
//                        }
//                        override fun onFinish() {
//                            binding.mangaSourceCountdownContainer.visibility = View.GONE
//                        }
//                    }
//                    timer?.start()
//                }
                binding.mangaSourceContainer.visibility = View.VISIBLE
                binding.mediaLoadProgressBar.visibility = View.GONE
                progress = View.GONE

                val sources : Array<String> = resources.getStringArray(R.array.manga_sources)
                binding.mangaSource.setText(sources[media.selected!!.source])
                binding.mangaSource.setAdapter(ArrayAdapter(requireContext(), R.layout.item_dropdown,sources))
                binding.mangaSource.setOnItemClickListener { _, _, i, _ ->
                    binding.mangaSourceRecycler.adapter = null
                    loading=true
                    binding.mangaSourceProgressBar.visibility=View.VISIBLE
                    media.selected!!.source = i
                    saveData(media.id.toString(), media.selected!!)
                    scope.launch{
                        model.loadMangaChapters(media,i,model)
                    }
                }
                selected = when(media.selected!!.recyclerStyle){
                    0->binding.mangaSourceList
                    1->binding.mangaSourceCompact
                    else -> binding.mangaSourceList
                }
                selected?.alpha = 1f
                binding.mangaSourceTop.rotation = if (!media.selected!!.recyclerReversed) 90f else -90f
                binding.mangaSourceTop.setOnClickListener {
                    binding.mangaSourceTop.rotation = if (media.selected!!.recyclerReversed) 90f else -90f
                    media.selected!!.recyclerReversed=!media.selected!!.recyclerReversed
                    saveData(media.id.toString(), media.selected!!)
                    updateRecycler(media)
                }
                binding.mangaSourceList.setOnClickListener {
                    media.selected!!.recyclerStyle=0
                    saveData(media.id.toString(), media.selected!!)
                    selected?.alpha = 0.33f
                    selected = binding.mangaSourceList
                    selected?.alpha = 1f
                    updateRecycler(media)
                }
                binding.mangaSourceCompact.setOnClickListener {
                    media.selected!!.recyclerStyle=1
                    saveData(media.id.toString(), media.selected!!)
                    selected?.alpha = 0.33f
                    selected = binding.mangaSourceCompact
                    selected?.alpha = 1f
                    updateRecycler(media)
                }

                model.getMangaChapters().observe(viewLifecycleOwner,{loadedChapters->
                    if(loadedChapters!=null) {
                        binding.mangaSourceChipGroup.removeAllViews()
                        val chapters = loadedChapters[media.selected!!.source]
                        if (chapters != null) {
                            media.manga.chapters = chapters
                            //CHIP GROUP
                            addPageChips(media, chapters.size)
                            updateRecycler(media)
                        }
                    }
                })

                scope.launch{
                    model.loadMangaChapters(media,media.selected!!.source,model)
                }

            }
        })
    }

    override fun onResume() {
        super.onResume()
        binding.mediaLoadProgressBar.visibility = progress
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    private fun updateRecycler(media: Media){
        if(media.manga?.chapters!=null) {
            binding.mangaSourceRecycler.adapter = mangaChapterAdapter(media, this, media.selected!!.recyclerStyle, media.selected!!.recyclerReversed, start, end)
            val gridCount = when (media.selected!!.recyclerStyle){
                0->1
                1->(screenWidth/200f).toInt()
                2->(screenWidth/80f).toInt()
                else->1
            }
            binding.mangaSourceRecycler.layoutManager = GridLayoutManager(requireContext(), gridCount)
            loading = false
            binding.mangaSourceProgressBar.visibility = View.GONE
            if(media.manga.chapters!!.isNotEmpty())
                binding.mangaSourceNotFound.visibility = View.GONE
            else
                binding.mangaSourceNotFound.visibility = View.VISIBLE
        }
    }
    fun onMangaChapterClick(media: Media, i:String){
        if (media.manga?.chapters?.get(i)!=null)
            media.manga.selectedChapter = i
        scope.launch{
            model.loadMangaChap(media.manga!!.chapters!![i]!!,media.selected!!.recyclerStyle)
        }
    }


    private fun addPageChips(media: Media, chapter: Int){
        val divisions = chapter.toDouble() / 10
        start = 0
        end = null
        val limit = when{
            (divisions < 25) -> 25
            (divisions < 50) -> 50
            else -> 100
        }
        if (chapter>limit) {
            val stored = ceil((chapter).toDouble() / limit).toInt()
            (1..stored).forEach {
                val chip = Chip(requireContext())
                chip.isCheckable = true

                if(it==1 && selectedChip==null){
                    selectedChip=chip
                    chip.isChecked = true
                }
                if (end == null) { end = limit * it - 1 }
                if (it == stored) {
                    chip.text = "${limit * (it - 1) + 1} - $chapter"
                    chip.setOnClickListener { _ ->
                        selectedChip?.isChecked = false
                        selectedChip = chip
                        selectedChip!!.isChecked = true
                        start = limit * (it - 1)
                        end = chapter - 1
                        updateRecycler(media)
                    }
                } else {
                    chip.text = "${limit * (it - 1) + 1} - ${limit * it}"
                    chip.setOnClickListener { _ ->
                        selectedChip?.isChecked = false
                        selectedChip = chip
                        selectedChip!!.isChecked = true
                        start = limit * (it - 1)
                        end = limit * it - 1
                        updateRecycler(media)
                    }
                }
                binding.mangaSourceChipGroup.addView(chip)
            }
        }
        else{
            updateRecycler(media)
        }
    }
}