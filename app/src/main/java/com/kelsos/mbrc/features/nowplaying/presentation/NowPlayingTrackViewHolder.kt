package com.kelsos.mbrc.features.nowplaying.presentation

import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kelsos.mbrc.R
import com.kelsos.mbrc.features.nowplaying.domain.NowPlaying
import com.kelsos.mbrc.features.nowplaying.dragsort.TouchHelperViewHolder
import com.kelsos.mbrc.ui.BindableViewHolder
import com.kelsos.mbrc.ui.OnViewItemPressed
import kotterknife.bindView

class NowPlayingTrackViewHolder(
  itemView: View,
  onHolderItemPressed: OnViewItemPressed,
  private val onDrag: (start: Boolean, holder: RecyclerView.ViewHolder) -> Unit
) : BindableViewHolder<NowPlaying>(itemView),
  TouchHelperViewHolder {

  private val title: TextView by bindView(R.id.track_title)
  private val artist: TextView by bindView(R.id.track_artist)
  private val trackPlaying: ImageView by bindView(R.id.track_indicator_view)
  private val dragHandle: View by bindView(R.id.drag_handle)

  init {
    itemView.setOnClickListener { onHolderItemPressed(adapterPosition) }
    dragHandle.setOnTouchListener { _, motionEvent ->
      if (motionEvent.action == MotionEvent.ACTION_DOWN) {
        onDrag(true, this)
      }
      true
    }
  }

  override fun onItemSelected() {
    this.itemView.setBackgroundColor(Color.DKGRAY)
  }

  override fun onItemClear() {
    this.itemView.setBackgroundColor(0)
    onDrag(false, this)
  }

  override fun bindTo(item: NowPlaying) {
    title.text = item.title
    artist.text = item.artist
  }

  fun setPlayingTrack(isPlayingTrack: Boolean) {
    trackPlaying.setImageResource(
      if (isPlayingTrack) {
        R.drawable.ic_media_now_playing
      } else {
        android.R.color.transparent
      }
    )
  }

  override fun clear() {
    title.text = ""
    artist.text = ""
  }

  companion object {
    fun create(
      parent: ViewGroup,
      onHolderItemPressed: OnViewItemPressed,
      onDrag: (start: Boolean, holder: RecyclerView.ViewHolder) -> Unit
    ): NowPlayingTrackViewHolder {
      val inflater: LayoutInflater = LayoutInflater.from(parent.context)
      val view = inflater.inflate(R.layout.ui_list_track_item, parent, false)
      return NowPlayingTrackViewHolder(
        view,
        onHolderItemPressed,
        onDrag
      )
    }
  }
}