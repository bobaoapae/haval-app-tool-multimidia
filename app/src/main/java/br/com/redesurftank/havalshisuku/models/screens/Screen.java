package br.com.redesurftank.havalshisuku.models.screens;

import br.com.redesurftank.havalshisuku.managers.ServiceManager;

public interface Screen {
    public static enum Key {
        UP, DOWN, ENTER, HOME, BACK, ENTER_LONG, BACK_LONG, UP_LONG, DOWN_LONG, LEFT, RIGHT
    }

    String getJsName();

    void processKey(Key key);

    void initialize();

    void setReturnScreen(Screen previousScreen);

}
