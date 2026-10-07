package br.com.redesurftank.displaypilot;

import android.app.Presentation;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;

/** Uses only the public Presentation API; never changes display resolution, tasks or window type. */
public final class PilotPresentation extends Presentation {
    private final Viewport.Pixels bounds;
    private boolean platformStarted;

    public PilotPresentation(Context context, Display display, Viewport.Pixels bounds) {
        super(context, display, android.R.style.Theme_Material_Light_NoActionBar);
        this.bounds = bounds;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        if (window == null) throw new IllegalStateException("Janela indisponível.");
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setFormat(PixelFormat.TRANSLUCENT);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.getDecorView().setElevation(0);
        WindowManager.LayoutParams attributes = window.getAttributes();
        // Physical left/top coordinates intentionally do not follow text direction.
        attributes.gravity = Gravity.TOP | Gravity.LEFT;
        attributes.x = bounds.x;
        attributes.y = bounds.y;
        attributes.width = bounds.width;
        attributes.height = bounds.height;
        attributes.horizontalMargin = 0;
        attributes.verticalMargin = 0;
        attributes.horizontalWeight = 0;
        attributes.verticalWeight = 0;
        attributes.dimAmount = 0;
        attributes.alpha = 1;
        window.setAttributes(attributes);
        setContentView(new MockNavigationView(getContext()));
        // Decor inflation can otherwise restore the theme animation when the value was zero.
        window.setWindowAnimations(0);
    }
    @Override protected void onStart() {
        // Android 9 Dialog.show calls onStart before WindowManager.addView can throw.
        platformStarted = true;
        super.onStart();
    }

    @Override protected void onStop() {
        if (platformStarted) {
            platformStarted = false;
            super.onStop();
        }
    }

    void cleanupFailedShow() {
        // dismiss() alone is a no-op when addView failed before Dialog.mShowing became true.
        if (!isShowing()) onStop();
    }
}
