package br.com.redesurftank.havalshisuku.projectors;

import android.app.Presentation;
import android.content.Context;
import android.view.Display;

/** Minimal collaborator fake: does not execute production projector or WebView code. */
public final class InstrumentProjector extends Presentation {
    public InstrumentProjector(Context context, Display display) { super(context, display); }
}
