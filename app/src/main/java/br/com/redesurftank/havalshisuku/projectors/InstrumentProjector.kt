package br.com.redesurftank.havalshisuku.projectors

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.widget.RelativeLayout
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import br.com.redesurftank.havalshisuku.managers.ServiceManager

class InstrumentProjector(outerContext: Context, display: Display) : BaseProjector(outerContext, display) {
    private val TAG = "InstrumentProjector"
    private lateinit var rootLayout: RelativeLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set window to transparent - it's invisible but keeps the display buffer refreshing
        window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        window?.addFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        rootLayout = RelativeLayout(context).apply {
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        setContentView(rootLayout)
        rootLayout.isVisible = ServiceManager.getInstance().isMainScreenOn
        Log.d(TAG, "InstrumentProjector (Display 1 Refresh Layer) created")
    }

    override fun carMainScreenOff() {
        ensureUi {
            rootLayout.isVisible = false
        }
    }

    override fun carMainScreenOn() {
        ensureUi {
            rootLayout.isVisible = true
        }
    }

    override fun cancel() {
        super.cancel()
    }
}

