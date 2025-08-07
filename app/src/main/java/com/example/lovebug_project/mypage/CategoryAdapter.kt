import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.lovebug_project.databinding.ItemCategoryBinding // 뷰 바인딩 클래스
import com.example.lovebug_project.mypage.CategoryData

class CategoryAdapter(private val categoryList: List<CategoryData>) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    // 1. ViewHolder: 아이템 1개의 뷰를 보관하는 홀더
    inner class CategoryViewHolder(private val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(category: CategoryData) {
            binding.tvCategoryName.text = category.cg_name
            binding.tvCategoryPercent.text = "${category.percentage}%"
            val background = binding.tvCategoryName.background as GradientDrawable
            background.setColor(Color.parseColor(category.color))

        }
    }

    // 2. onCreateViewHolder: ViewHolder를 새로 만들어야 할 때 호출됨
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryViewHolder(binding)
    }

    // 3. onBindViewHolder: ViewHolder에 데이터를 연결할 때 호출됨
    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categoryList[position])
    }

    // 4. getItemCount: 전체 아이템 개수를 반환
    override fun getItemCount(): Int {
        return categoryList.size
    }
}