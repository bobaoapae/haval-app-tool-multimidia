package com.google.android.projection.protocol;
import com.google.android.projection.proto.Protos;
/** Compile-only declaration; no OEM implementation. */
public class VideoSink implements CarServiceProvider {
 public static abstract class ProjectionListener {
  public ProjectionListener(){}
  public void onCodecConfig(byte[] bytes){}
  public void onCodecSetup(int type){}
  public void onProjectionUpdate(VideoFrame frame){}
  public void onVideoFocusRequest(int mode,int reason){}
 }
 public VideoSink(ProjectionListener listener,boolean autoStart,int viewingDistance){}
 public boolean create(int id,long receiver){throw new UnsupportedOperationException();}
 public void destroy(){throw new UnsupportedOperationException();}
 public long getNativeInstance(){throw new UnsupportedOperationException();}
 public void setDisplayIdAndType(int id,Protos.DisplayType type){throw new UnsupportedOperationException();}
 public void setCodecType(Protos.MediaCodecType type){throw new UnsupportedOperationException();}
 public void addSupportedConfiguration(Protos.VideoConfiguration config){throw new UnsupportedOperationException();}
 public void ackFrames(int session,int count){throw new UnsupportedOperationException();}
 public void setVideoFocus(int mode,int reason,boolean unsolicited){throw new UnsupportedOperationException();}
}
