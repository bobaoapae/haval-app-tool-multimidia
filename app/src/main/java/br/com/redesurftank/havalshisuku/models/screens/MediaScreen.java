package br.com.redesurftank.havalshisuku.models.screens;

import br.com.redesurftank.havalshisuku.managers.ServiceManager;
import br.com.redesurftank.havalshisuku.models.MainUiManager;
import br.com.redesurftank.havalshisuku.models.ServiceManagerEventType;

public class MediaScreen implements Screen {
    private ServiceManager serviceManager;
    private Screen previousScreen = this;

    @Override
    public String getJsName() {
        return "media";
    }

    @Override
    public void initialize() {
        this.serviceManager = ServiceManager.getInstance();
        serviceManager.dispatchServiceManagerEvent(ServiceManagerEventType.UPDATE_SCREEN, this);
    }

    @Override
    public void processKey(Key key) {
        switch (key) {
            case BACK:
            case BACK_LONG:
                MainUiManager.getInstance().updateScreen(previousScreen);
                break;
            case UP:
            case UP_LONG:
                serviceManager.dispatchServiceManagerEvent(ServiceManagerEventType.RADIO_NAVIGATE, "next");
                break;
            case DOWN:
            case DOWN_LONG:
                serviceManager.dispatchServiceManagerEvent(ServiceManagerEventType.RADIO_NAVIGATE, "prev");
                break;
            case ENTER:
                serviceManager.dispatchServiceManagerEvent(ServiceManagerEventType.RADIO_PLAY_PAUSE);
                break;
        }
    }

    @Override
    public void setReturnScreen(Screen previousScreen) {
        this.previousScreen = previousScreen;
    }
}
