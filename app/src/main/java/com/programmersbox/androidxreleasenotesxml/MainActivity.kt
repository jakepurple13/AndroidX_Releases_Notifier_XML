package com.programmersbox.androidxreleasenotesxml

import android.content.Intent
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.programmersbox.helpfulutils.layoutInflater
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val vm: ReleaseNotesViewModel by lazy { defaultViewModelProviderFactory.create(ReleaseNotesViewModel::class.java) }

    private val adapter by lazy { AndroidXAdapter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val swipe = findViewById<SwipeRefreshLayout>(R.id.swiperefresh)
        swipe?.setOnRefreshListener { vm.refreshItems() }
        lifecycleScope.launch {
            vm.notes.collect {
                adapter.updateList(it)
                swipe.isRefreshing = false
            }
        }
        findViewById<RecyclerView>(R.id.androidx_rv)?.let { rv ->
            rv.adapter = adapter
            rv.addItemDecoration(StickHeaderItemDecoration(adapter))
        }

        vm.refreshItems()
    }
}

class AndroidXAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>(), StickHeaderItemDecoration.StickyHeaderInterface {

    private val list: MutableList<Notes> = mutableListOf()

    fun updateList(list: List<Notes>) {
        this.list.clear()
        this.list.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        1 -> TitleHolder(parent.context.layoutInflater.inflate(R.layout.title_item, parent, false))
        else -> AndroidXHolder(parent.context.layoutInflater.inflate(R.layout.androidx_item, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AndroidXHolder -> (list.getOrNull(position) as? ReleaseNotes)?.let(holder::bind)
            is TitleHolder -> (list.getOrNull(position) as? DateNotes)?.let(holder::bind)
        }
    }

    override fun getItemCount(): Int = list.size

    override fun getItemViewType(position: Int): Int = when (list[position]) {
        is DateNotes -> 1
        is ReleaseNotes -> 2
    }

    override fun getHeaderPositionForItem(itemPosition: Int): Int {
        var iPosition = itemPosition
        var headerPosition = 0
        do {
            if (isHeader(iPosition)) {
                headerPosition = iPosition
                break
            }
            iPosition -= 1
        } while (itemPosition >= 0)
        return headerPosition
    }

    override fun getHeaderLayout(headerPosition: Int): Int {
        return when (list[headerPosition]) {
            is DateNotes -> R.layout.title_item
            is ReleaseNotes -> R.layout.androidx_item
        }
    }

    override fun bindHeaderData(header: View?, headerPosition: Int) {
        header?.findViewById<TextView>(R.id.title_content)?.text = list[headerPosition].date
    }

    override fun isHeader(itemPosition: Int): Boolean = when (list[itemPosition]) {
        is DateNotes -> true
        is ReleaseNotes -> false
    }
}

class AndroidXHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    fun bind(item: ReleaseNotes) {
        itemView.findViewById<TextView>(R.id.androidx_content)?.text =
            Html.fromHtml(item.content.removePrefix("<![CDATA[ ").removeSuffix(" ]]>"), Html.FROM_HTML_MODE_COMPACT).toString()

        itemView.setOnClickListener {
            val url = item.link
            val i = Intent(Intent.ACTION_VIEW)
            i.data = Uri.parse(url)
            itemView.context.startActivity(i)
        }
    }
}

class TitleHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    fun bind(item: DateNotes) {
        itemView.findViewById<TextView>(R.id.title_content)?.text = item.date
    }
}

class StickHeaderItemDecoration(private val mListener: StickyHeaderInterface) : RecyclerView.ItemDecoration() {
    private var mStickyHeaderHeight = 0
    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)
        val topChild = parent.getChildAt(0) ?: return
        val topChildPosition = parent.getChildAdapterPosition(topChild)
        if (topChildPosition == RecyclerView.NO_POSITION) {
            return
        }
        val headerPos = mListener.getHeaderPositionForItem(topChildPosition)
        val currentHeader = getHeaderViewForItem(headerPos, parent)
        fixLayoutSize(parent, currentHeader)
        val contactPoint = currentHeader.bottom
        val childInContact = getChildInContact(parent, contactPoint, headerPos)
        if (childInContact != null && mListener.isHeader(parent.getChildAdapterPosition(childInContact))) {
            moveHeader(c, currentHeader, childInContact)
            return
        }
        drawHeader(c, currentHeader)
    }

    private fun getHeaderViewForItem(headerPosition: Int, parent: RecyclerView): View {
        val layoutResId = mListener.getHeaderLayout(headerPosition)
        val header = LayoutInflater.from(parent.context).inflate(layoutResId, parent, false)
        mListener.bindHeaderData(header, headerPosition)
        return header
    }

    private fun drawHeader(c: Canvas, header: View) {
        c.save()
        c.translate(0f, 0f)
        header.draw(c)
        c.restore()
    }

    private fun moveHeader(c: Canvas, currentHeader: View, nextHeader: View) {
        c.save()
        c.translate(0f, (nextHeader.top - currentHeader.height).toFloat())
        currentHeader.draw(c)
        c.restore()
    }

    private fun getChildInContact(parent: RecyclerView, contactPoint: Int, currentHeaderPos: Int): View? {
        var childInContact: View? = null
        for (i in 0 until parent.childCount) {
            var heightTolerance = 0
            val child = parent.getChildAt(i)

            //measure height tolerance with child if child is another header
            if (currentHeaderPos != i) {
                val isChildHeader = mListener.isHeader(parent.getChildAdapterPosition(child))
                if (isChildHeader) {
                    heightTolerance = mStickyHeaderHeight - child.height
                }
            }

            //add heightTolerance if child top be in display area
            val childBottomPosition: Int = if (child.top > 0) {
                child.bottom + heightTolerance
            } else {
                child.bottom
            }
            if (childBottomPosition > contactPoint) {
                if (child.top <= contactPoint) {
                    // This child overlaps the contactPoint
                    childInContact = child
                    break
                }
            }
        }
        return childInContact
    }

    private fun fixLayoutSize(parent: ViewGroup, view: View) {
        // Specs for parent (RecyclerView)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.UNSPECIFIED)

        // Specs for children (headers)
        val childWidthSpec = ViewGroup.getChildMeasureSpec(widthSpec, parent.paddingLeft + parent.paddingRight, view.layoutParams.width)
        val childHeightSpec = ViewGroup.getChildMeasureSpec(heightSpec, parent.paddingTop + parent.paddingBottom, view.layoutParams.height)
        view.measure(childWidthSpec, childHeightSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight.also { mStickyHeaderHeight = it })
    }

    interface StickyHeaderInterface {
        fun getHeaderPositionForItem(itemPosition: Int): Int
        fun getHeaderLayout(headerPosition: Int): Int
        fun bindHeaderData(header: View?, headerPosition: Int)
        fun isHeader(itemPosition: Int): Boolean
    }
}
