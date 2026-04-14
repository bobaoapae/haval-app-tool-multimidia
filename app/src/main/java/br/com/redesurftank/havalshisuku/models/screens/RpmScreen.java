package br.com.redesurftank.havalshisuku.models.screens;

import br.com.redesurftank.havalshisuku.managers.ServiceManager;
import br.com.redesurftank.havalshisuku.models.MainUiManager;
import br.com.redesurftank.havalshisuku.models.ServiceManagerEventType;

public class RpmScreen implements Screen {

    private ServiceManager serviceManager;
    private Screen previousScreen = this;

    @Override
    public String getJsName() {
        return "rpm";
    }

    @Override
    public void processKey(Key key) {
        switch (key) {
            case BACK:
            case BACK_LONG:
                MainUiManager.getInstance().updateScreen(previousScreen);
                break;
        }
    }

    @Override
    public void initialize() {
        this.serviceManager = ServiceManager.getInstance();
        serviceManager.dispatchServiceManagerEvent(ServiceManagerEventType.UPDATE_SCREEN, this);
    }

    @Override
    public void setReturnScreen(Screen previousScreen) {
        this.previousScreen = previousScreen;
    }
}
