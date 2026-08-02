package Ir.co.tfs.farazaman.activity

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.databinding.ItemIntroSlideBinding
import Ir.co.tfs.farazaman.databinding.ItemIntroSlidePage1Binding
import Ir.co.tfs.farazaman.databinding.ItemIntroSlidePage2Binding
import Ir.co.tfs.farazaman.databinding.ItemIntroSlidePage3Binding

data class IntroSlide(
    @LayoutRes val layoutRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
) {
    companion object {
        fun page1(titleRes: Int, descriptionRes: Int) = IntroSlide(
            layoutRes = R.layout.item_intro_slide_page1,
            titleRes = titleRes,
            descriptionRes = descriptionRes,
        )

        fun page2(titleRes: Int, descriptionRes: Int) = IntroSlide(
            layoutRes = R.layout.item_intro_slide_page2,
            titleRes = titleRes,
            descriptionRes = descriptionRes,
        )

        fun page3(titleRes: Int, descriptionRes: Int) = IntroSlide(
            layoutRes = R.layout.item_intro_slide_page3,
            titleRes = titleRes,
            descriptionRes = descriptionRes,
        )

        fun default(titleRes: Int, descriptionRes: Int) = IntroSlide(
            layoutRes = R.layout.item_intro_slide,
            titleRes = titleRes,
            descriptionRes = descriptionRes,
        )
    }
}

class IntroSlideAdapter(
    private val slides: List<IntroSlide>,
) : RecyclerView.Adapter<IntroSlideAdapter.SlideViewHolder>() {

    override fun getItemViewType(position: Int): Int = slides[position].layoutRes

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            R.layout.item_intro_slide_page1 -> {
                ContentViewHolder(ItemIntroSlidePage1Binding.inflate(inflater, parent, false))
            }
            R.layout.item_intro_slide_page2 -> {
                ContentViewHolder(ItemIntroSlidePage2Binding.inflate(inflater, parent, false))
            }
            R.layout.item_intro_slide_page3 -> {
                ContentViewHolder(ItemIntroSlidePage3Binding.inflate(inflater, parent, false))
            }
            else -> {
                DefaultViewHolder(ItemIntroSlideBinding.inflate(inflater, parent, false))
            }
        }
    }

    override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
        holder.bind(slides[position])
    }

    override fun getItemCount(): Int = slides.size

    abstract class SlideViewHolder(root: android.view.View) : RecyclerView.ViewHolder(root) {
        abstract fun bind(slide: IntroSlide)
    }

    class ContentViewHolder(
        root: android.view.View,
        private val titleView: android.widget.TextView,
        private val descriptionView: android.widget.TextView,
    ) : SlideViewHolder(root) {

        constructor(binding: ItemIntroSlidePage1Binding) : this(
            binding.root,
            binding.introTitle,
            binding.introDescription,
        )

        constructor(binding: ItemIntroSlidePage2Binding) : this(
            binding.root,
            binding.introTitle,
            binding.introDescription,
        )

        constructor(binding: ItemIntroSlidePage3Binding) : this(
            binding.root,
            binding.introTitle,
            binding.introDescription,
        )

        override fun bind(slide: IntroSlide) {
            titleView.setText(slide.titleRes)
            descriptionView.setText(slide.descriptionRes)
        }
    }

    class DefaultViewHolder(
        private val binding: ItemIntroSlideBinding,
    ) : SlideViewHolder(binding.root) {
        override fun bind(slide: IntroSlide) {
            binding.introTitle.setText(slide.titleRes)
            binding.introDescription.setText(slide.descriptionRes)
        }
    }
}
