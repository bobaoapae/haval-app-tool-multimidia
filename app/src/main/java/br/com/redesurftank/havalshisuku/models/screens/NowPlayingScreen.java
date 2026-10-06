package br.com.redesurftank.havalshisuku.models.screens;

import br.com.redesurftank.havalshisuku.managers.ServiceManager;
import br.com.redesurftank.havalshisuku.models.MainUiManager;
import br.com.redesurftank.havalshisuku.models.ServiceManagerEventType;

/** Read-only cluster screen. Media state is supplied by InstrumentProjector2. */
public class NowPlayingScreen implements Screen {
    private Screen previousScreen = this;

    @Override
    public String getJsName() {
        return "now_playing";
    }

    @Override
    public void initialize() {
        ServiceManager.getInstance().dispatchServiceManagerEvent(
                ServiceManagerEventType.UPDATE_SCREEN, this);
    }

    @Override
    public void processKey(Key key) {
        if (key == Key.BACK || key == Key.BACK_LONG) {
            MainUiManager.getInstance().updateScreen(previousScreen);
        }
    }

    @Override
    public void setReturnScreen(Screen previousScreen) {
        this.previousScreen = previousScreen;
    }
}
