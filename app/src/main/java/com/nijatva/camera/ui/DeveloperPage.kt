package com.nijatva.camera.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nijatva.camera.R
import kotlin.math.roundToInt

/**
 * The Developer page, in the layout the Nijatva site uses: back link, avatar with
 * initials, name and handle, two lines of description, a three-column figures card,
 * contact rows, a white "View GitHub Profile" button, and the maintained apps.
 *
 * The figures are the site's published numbers, with the date they were read
 * attached, rather than something fetched at runtime: this app has no INTERNET
 * permission and that is deliberate.
 */
class DeveloperPage(context: Context) : FrameLayout(context) {

    var onClose: (() -> Unit)? = null

    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(28))
    }

    init {
        visibility = View.GONE
        isClickable = true
        setBackgroundColor(Palette.background)

        val scroller = ScrollView(context).apply { isVerticalScrollBarEnabled = false }
        scroller.addView(content)
        addView(scroller, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    fun open() {
        build()
        visibility = View.VISIBLE
        bringToFront()
    }

    fun close() {
        visibility = View.GONE
    }

    private fun build() {
        content.removeAllViews()
        setBackgroundColor(Palette.background)

        // ---- back row
        val back = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setOnClickListener { onClose?.invoke() }
        }
        back.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_back)
            setColorFilter(Palette.onSurface)
        }, LinearLayout.LayoutParams(dp(20), dp(20)))
        back.addView(TextView(context).apply {
            text = context.getString(R.string.developer_title)
            setTextColor(Palette.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(dp(10), dp(6), 0, dp(6))
        })
        content.addView(back, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(22) })

        // ---- avatar
        content.addView(TextView(context).apply {
            text = "VD"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Palette.accent)
                setStroke(dp(3), Palette.surface)
            }
        }, LinearLayout.LayoutParams(dp(104), dp(104)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(6)
            bottomMargin = dp(16)
        })

        content.addView(centred(context.getString(R.string.developer_name), 25f, bold = true, colour = Palette.onSurface))
        content.addView(centred(context.getString(R.string.developer_handle), 14f, colour = Palette.muted))

        content.addView(centred(
            context.getString(R.string.developer_bio_one), 14f, colour = Palette.onSurface,
        ).apply { setPadding(dp(8), dp(18), dp(8), 0) })
        content.addView(centred(
            context.getString(R.string.developer_bio_two), 14f, colour = Palette.onSurface,
        ).apply { setPadding(dp(8), dp(8), dp(8), 0) })

        // ---- figures card
        val figures = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = card()
            setPadding(0, dp(18), 0, dp(18))
        }
        figures.addView(figureCell(context.getString(R.string.developer_repos), context.getString(R.string.developer_repos_label)), weighted())
        figures.addView(divider(), LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT))
        figures.addView(figureCell(context.getString(R.string.developer_apps), context.getString(R.string.developer_apps_label)), weighted())
        figures.addView(divider(), LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT))
        figures.addView(figureCell(context.getString(R.string.developer_with_code), context.getString(R.string.developer_with_code_label)), weighted())
        content.addView(figures, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(22) })

        // ---- contact rows
        val rows = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(22), 0, 0)
        }
        rows.addView(infoRow(R.drawable.ic_pin, context.getString(R.string.developer_country), null))
        rows.addView(infoRow(R.drawable.ic_link, context.getString(R.string.developer_site), "https://vasudev-work.github.io"))
        rows.addView(infoRow(R.drawable.ic_mail, context.getString(R.string.developer_email), "mailto:Vasudev.Social@rediffmail.com"))
        content.addView(rows)

        // ---- GitHub button
        val button = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(if (Palette.isLight) Palette.onSurface else Color.WHITE)
            }
            isClickable = true
            setOnClickListener {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/vasudev-work"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
        button.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_git)
            setColorFilter(if (Palette.isLight) Color.WHITE else Color.parseColor("#0B0B0C"))
        }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { rightMargin = dp(10) })
        button.addView(TextView(context).apply {
            text = context.getString(R.string.developer_view_github)
            setTextColor(if (Palette.isLight) Color.WHITE else Color.parseColor("#0B0B0C"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        })
        content.addView(button, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
        ).apply { topMargin = dp(22) })

        // ---- maintains
        content.addView(TextView(context).apply {
            text = context.getString(R.string.developer_maintains)
            setTextColor(Palette.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(28) })

        val tiles = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        tiles.addView(appTile(R.drawable.ic_camera_tile, Palette.accent))
        tiles.addView(appTile(R.drawable.ic_globe, AccentChoiceIndigo), LinearLayout.LayoutParams(dp(44), dp(44)).apply { leftMargin = dp(10) })
        tiles.addView(TextView(context).apply {
            text = context.getString(R.string.developer_plus_more)
            setTextColor(Palette.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Palette.surface)
                setStroke(dp(1), Palette.hairline)
            }
        }, LinearLayout.LayoutParams(dp(52), dp(44)).apply { leftMargin = dp(10) })
        content.addView(tiles)

        content.addView(TextView(context).apply {
            text = context.getString(R.string.developer_footnote)
            setTextColor(Palette.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setLineSpacing(dp(4).toFloat(), 1f)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })
    }

    private fun figureCell(number: String, caption: String): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(TextView(context).apply {
            text = number
            setTextColor(Palette.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        })
        addView(TextView(context).apply {
            text = caption
            setTextColor(Palette.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        })
    }

    private fun infoRow(icon: Int, text: String, link: String?): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(8), 0, dp(8))
        addView(ImageView(context).apply {
            setImageResource(icon)
            setColorFilter(Palette.muted)
        }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { rightMargin = dp(12) })
        addView(TextView(context).apply {
            this.text = text
            setTextColor(Palette.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            if (link != null) {
                paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                isClickable = true
                setOnClickListener {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(link))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }
        })
    }

    private fun appTile(icon: Int, colour: Int): ImageView = ImageView(context).apply {
        setImageResource(icon)
        setColorFilter(Color.WHITE)
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(colour)
        }
        setPadding(dp(10), dp(10), dp(10), dp(10))
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
    }

    private fun card(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(20).toFloat()
        setColor(Palette.surface)
        setStroke(dp(1), Palette.hairline)
    }

    private fun divider(): View = View(context).apply {
        setBackgroundColor(Palette.hairline)
    }

    private fun weighted(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun centred(text: String, sizeSp: Float, bold: Boolean = false, colour: Int): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(colour)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1f)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        /** The indigo from the site's accent row, used for the second app tile. */
        val AccentChoiceIndigo: Int = 0xFF3F45A8.toInt()
    }
}
