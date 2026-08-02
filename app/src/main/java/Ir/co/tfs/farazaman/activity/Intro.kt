package Ir.co.tfs.farazaman.activity

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import Ir.co.tfs.farazaman.OSMTracker
import Ir.co.tfs.farazaman.R
import Ir.co.tfs.farazaman.databinding.ActivityIntroBinding

class Intro : AppCompatActivity() {

    private lateinit var binding: ActivityIntroBinding
    private val slides = listOf(
        IntroSlide.page1(
            titleRes = R.string.app_intro_slide1_title,
            descriptionRes = R.string.app_intro_slide1_description,
        ),
        IntroSlide.page2(
            titleRes = R.string.app_intro_slide2_title,
            descriptionRes = R.string.app_intro_slide2_description,
        ),
        IntroSlide.page3(
            titleRes = R.string.app_intro_slide3_title,
            descriptionRes = R.string.app_intro_slide3_description,
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIntroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.introViewPager.adapter = IntroSlideAdapter(slides)
        binding.introViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateUiForPage(position)
            }
        })

        binding.btnIntroAction.setOnClickListener { onActionPressed() }
        binding.btnSkipIntro.setOnClickListener { onActionPressed() }

        updateUiForPage(0)
    }

    private fun onActionPressed() {
        val current = binding.introViewPager.currentItem
        if (current < slides.lastIndex) {
            binding.introViewPager.setCurrentItem(current + 1, true)
        } else {
            finishIntro()
        }
    }

    private fun updateUiForPage(position: Int) {
        binding.btnIntroAction.text = if (position == slides.lastIndex) {
            getString(R.string.intro_start)
        } else {
            getString(R.string.intro_next)
        }
        binding.btnSkipIntro.visibility = if (position == slides.lastIndex) View.GONE else View.VISIBLE
        updateDots(position)
    }

    private fun updateDots(activeIndex: Int) {
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3)
        dots.forEachIndexed { index, dot ->
            val isActive = index == activeIndex
            val sizeDp = if (isActive) 10 else 8
            val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
            dot.layoutParams = (dot.layoutParams as LinearLayout.LayoutParams).apply {
                width = sizePx
                height = sizePx
            }
            dot.background = ContextCompat.getDrawable(
                this,
                if (isActive) R.drawable.intro_dot_active else R.drawable.intro_dot_inactive,
            )
        }
    }

    private fun finishIntro() {
        PreferenceManager.getDefaultSharedPreferences(baseContext).edit()
            .putBoolean(OSMTracker.Preferences.KEY_DISPLAY_APP_INTRO, false)
            .apply()
        startActivity(Intent(this, RoleSelectionActivity::class.java))
        finish()
    }
}
