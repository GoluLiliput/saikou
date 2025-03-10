package ani.saikou

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.GridLayoutManager
import ani.saikou.anilist.Anilist
import ani.saikou.databinding.ActivityGenreBinding
import ani.saikou.media.GenreAdapter

class GenreActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGenreBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGenreBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initActivity(this)
        binding.genreContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight;bottomMargin+= navBarHeight }
        val screenWidth = resources.displayMetrics.run { widthPixels / density }
        binding.mediaInfoGenresRecyclerView.adapter = GenreAdapter(Anilist.genres?.keys?.toList() as ArrayList<String>,intent.getStringExtra("type")!!,this,true)
        binding.mediaInfoGenresRecyclerView.layoutManager = GridLayoutManager(this, (screenWidth/156f).toInt())
    }
}