package com.marcinmoskala.videoplayview

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.VideoView
import com.marcinmoskala.videoplayview.VideoPlayView.State.*
import kotlin.properties.Delegates.observable


class VideoPlayView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
        defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    sealed class State {
        object Initial : State()
        object Loading : State()
        object Ready : State()
        object Playing : State()
        object Paused : State()
    }

    var state: State by observable(Initial as State) { _, prevState, state ->
        loadingView.visibility = if (state is Loading) View.VISIBLE else View.GONE
        playView.visibility = if (state is Ready || state is Paused) View.VISIBLE else View.GONE
        imageView.visibility = if (state is Playing) View.GONE else View.VISIBLE
        when (state) {
            is Playing -> videoView.start()
            is Paused -> videoView.pause()
            is Ready -> videoView.seekTo(0)
        }
    }

    var videoUrl: String? by observable(null as String?) { _, _, videoUrl ->
        require(!videoUrl.isNullOrBlank()) { "videoUrl is cannot be null or blank" }
        require(state is Initial) { "state must be Initial" }
        videoView.setVideoPath(videoUrl)
        if (state is Initial) {
            state = Loading
        }
        videoView.setOnPreparedListener {
            onVideoReadyListener?.invoke()
            state = if (autoplay) Playing else Ready
        }
    }

    var looping: Boolean = false
    var autoplay: Boolean = false
    var stopOnClick: Boolean = false

    var onVideoReadyListener: (() -> Unit)? = null
    var onVideoFinishedListener: (() -> Unit)? = null

    private val view by lazy { View.inflate(context, R.layout.view_video_play, null) }
    private val videoView: VideoView by view.bindView(R.id.videoView)
    val imageView: ImageView by view.bindView(R.id.imageView)
    val playView: ImageView by view.bindView(R.id.playView)
    val loadingView: ImageView by view.bindView(R.id.loadingView)

    init {
        val attrSet = context.theme.obtainStyledAttributes(attrs, R.styleable.VideoPlayView, defStyleAttr, defStyleAttr)
        try {
            looping = attrSet.getBoolean(R.styleable.VideoPlayView_loop, false)
            autoplay = attrSet.getBoolean(R.styleable.VideoPlayView_autoplay, false)
            stopOnClick = attrSet.getBoolean(R.styleable.VideoPlayView_stopOnClick, false)
            attrSet.getDrawable(R.styleable.VideoPlayView_playButton)?.let(playView::setImageDrawable)
            attrSet.getDrawable(R.styleable.VideoPlayView_loadingButton)?.let(loadingView::setImageDrawable)
            attrSet.getDrawable(R.styleable.VideoPlayView_image)?.let(imageView::setImageDrawable)
            attrSet.getText(R.styleable.VideoPlayView_videoUrl)?.toString()?.let { videoUrl = it }
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
                imageView.background = background
            } else {
                imageView.setBackgroundColor(getBackgroundColor())
            }
        } finally {
            attrSet.recycle()
        }
        addView(view)
        videoView.touchBasedOnClick {
            when {
                state is Playing && !stopOnClick -> state = Paused
                state is Playing && stopOnClick -> state = Ready
                state is Paused -> state = Playing
            }
        }
        imageView.setOnClickListener {
            if (state is Paused || state is Ready) state = Playing
        }
        videoView.setOnCompletionListener {
            onVideoFinishedListener?.invoke()
            state = if (looping) Playing else Ready
        }
        playView.setOnClickListener {
            state = Playing
        }
        state = state
    }
}