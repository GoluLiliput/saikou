package ani.saikou.media

import android.os.Bundle
import android.text.InputFilter.LengthFilter
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.activityViewModels
import ani.saikou.*
import ani.saikou.anilist.Anilist
import ani.saikou.anilist.AnilistHomeViewModel
import ani.saikou.databinding.BottomSheetMediaListBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MediaListDialogFragment : BottomSheetDialogFragment(){

    private var _binding: BottomSheetMediaListBinding? = null
    private val binding get() = _binding!!
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetMediaListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.mediaListContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin += navBarHeight }
        var media : Media?
        val model : MediaDetailsViewModel by activityViewModels()
        model.getMedia().observe(this,{
            media = it
            if (media!=null){
                binding.mediaListProgressBar.visibility = View.GONE
                binding.mediaListLayout.visibility = View.VISIBLE

                val statuses : Array<String> = resources.getStringArray(R.array.status)
                binding.mediaListStatus.setText(if(media!!.userStatus!=null) media!!.userStatus else statuses[0])
                binding.mediaListStatus.setAdapter(ArrayAdapter(requireContext(), R.layout.item_dropdown,statuses))


                var total:Int?=null
                binding.mediaListProgress.setText(if (media!!.userProgress!=null) media!!.userProgress.toString() else "")
                if (media!!.anime!=null) if (media!!.anime!!.totalEpisodes!=null){ total = media!!.anime!!.totalEpisodes!!;binding.mediaListProgress.filters = arrayOf(InputFilterMinMax(0.0, total.toDouble(),binding.mediaListStatus), LengthFilter(total.toString().length)) }
                else if (media!!.manga!=null) if (media!!.manga!!.totalChapters!=null){ total = media!!.manga!!.totalChapters!!;binding.mediaListProgress.filters = arrayOf(InputFilterMinMax(0.0, total.toDouble(),binding.mediaListStatus), LengthFilter(total.toString().length)) }
                binding.mediaListProgressLayout.suffixText = " / ${total?:'?'}"
                binding.mediaListProgressLayout.suffixTextView.updateLayoutParams { height = ViewGroup.LayoutParams.MATCH_PARENT }
                binding.mediaListProgressLayout.suffixTextView.gravity = Gravity.CENTER

                binding.mediaListScore.setText(if (media!!.userScore!=0) media!!.userScore.div(10.0).toString() else "")
                binding.mediaListScore.filters = arrayOf(InputFilterMinMax(1.0, 10.0), LengthFilter(10.0.toString().length))
                binding.mediaListScoreLayout.suffixTextView.updateLayoutParams { height = ViewGroup.LayoutParams.MATCH_PARENT }
                binding.mediaListScoreLayout.suffixTextView.gravity = Gravity.CENTER

                val start = DatePickerFragment(requireActivity(), media!!.userStartedAt)
                val end = DatePickerFragment(requireActivity(), media!!.userCompletedAt)
                binding.mediaListStart.setText((if (media!!.userStartedAt.year!=null) media!!.userStartedAt else "").toString())
                binding.mediaListStart.setOnClickListener { try{ if (!start.dialog.isShowing) start.dialog.show() }catch(e:Exception){} }
                binding.mediaListStart.setOnFocusChangeListener { _, b -> try{if (b && !start.dialog.isShowing) start.dialog.show() }catch(e:Exception){} }
                binding.mediaListEnd.setText((if (media!!.userCompletedAt.year!=null) media!!.userCompletedAt else "").toString())
                binding.mediaListEnd.setOnClickListener { try{if (!end.dialog.isShowing) end.dialog.show() }catch(e:Exception){} }
                binding.mediaListEnd.setOnFocusChangeListener { _, b -> try{if (b && !end.dialog.isShowing) end.dialog.show() }catch(e:Exception){} }
                start.dialog.setOnDismissListener { binding.mediaListStart.setText(start.date.toString()) }
                end.dialog.setOnDismissListener { binding.mediaListEnd.setText(end.date.toString()) }


                fun onComplete(){
                    binding.mediaListProgress.setText(total.toString())
                    if(start.date.year==null) {
                        start.date = FuzzyDate().getToday()
                        binding.mediaListStart.setText(start.date.toString())
                    }
                    end.date=FuzzyDate().getToday()
                    binding.mediaListEnd.setText(end.date.toString())
                }
                var startBackupDate : FuzzyDate? = null
                var endBackupDate : FuzzyDate? = null
                var progressBackup : String? = null
                binding.mediaListStatus.setOnItemClickListener { _, _, i, _ ->
                    if (i==2 && total!=null) {
//                        println("Com : ${start.date} ${end.date}")
                        startBackupDate = start.date
                        endBackupDate = end.date
                        progressBackup = binding.mediaListProgress.text.toString()
                        onComplete()
                    }
                    else{
                        if (progressBackup!=null) binding.mediaListProgress.setText(progressBackup)
                        if (startBackupDate!=null) {
                            binding.mediaListStart.setText(startBackupDate.toString())
                            start.date = startBackupDate!!
                        }
                        if (endBackupDate!=null) {
//                            println("back : $endBackupDate")
                            binding.mediaListEnd.setText(endBackupDate.toString())
                            end.date = endBackupDate!!
                        }
                    }
                }

                binding.mediaListIncrement.setOnClickListener {
                    if(binding.mediaListStatus.text.toString() == statuses[0]) binding.mediaListStatus.setText(statuses[1],false)
                    val init = if(binding.mediaListProgress.text.toString()!="") binding.mediaListProgress.text.toString().toInt() else 0
                    if (init<total?:5000) binding.mediaListProgress.setText((init+1).toString())
                    if(init+1==total?:5000) {
                        binding.mediaListStatus.setText(statuses[2], false)
                        onComplete()
                    }
                }

                binding.mediaListSave.setOnClickListener {
                    scope.launch {
                        Anilist.mutation.editList(
                        media!!.id,
                        if (_binding?.mediaListProgress?.text.toString()!="") _binding?.mediaListProgress?.text.toString().toInt() else null,
                        if (_binding?.mediaListScore?.text.toString()!="") (_binding?.mediaListScore?.text.toString().toDouble()*10).toInt() else null,
                        if (_binding?.mediaListStatus?.text.toString()!="") _binding?.mediaListStatus?.text.toString() else null,
                        if (start.date.year!=null) start.date.getEpoch() else null,
                        if (end.date.year!=null) end.date.getEpoch() else null,
                        )
                        activity?.runOnUiThread {
                            val m : AnilistHomeViewModel by activityViewModels()
                            m.homeRefresh.postValue(true)
                            if (_binding?.mediaListScore?.text.toString()!="") model.userScore.postValue(_binding?.mediaListScore?.text.toString().toDouble()*10)
                            if (_binding?.mediaListProgress?.text.toString()!="") model.userProgress.postValue(_binding?.mediaListProgress?.text.toString().toInt())
                            model.userStatus.postValue(_binding?.mediaListStatus?.text.toString())
                            dismiss()
                        }
                    }
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
