package com.google.android.projection.protocol;
import android.view.KeyEvent;import android.view.MotionEvent;
/** Compile-only declaration; no OEM implementation. */
public class InputSource implements CarServiceProvider {
 public interface InputInjector{void onKeyEvent(KeyEvent e);void onMotionEvent(MotionEvent e);void onRelativeEvent(int x,int y);}
 public InputSource(InputInjector injector){}
 public boolean create(int id,long receiver){throw new UnsupportedOperationException();}
 public void destroy(){throw new UnsupportedOperationException();}
 public long getNativeInstance(){throw new UnsupportedOperationException();}
 public void setDisplayId(int id){throw new UnsupportedOperationException();}
 public void registerKeyCodes(int[] keys){throw new UnsupportedOperationException();}
}
