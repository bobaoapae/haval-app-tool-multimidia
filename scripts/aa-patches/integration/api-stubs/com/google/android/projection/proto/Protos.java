package com.google.android.projection.proto;
import com.google.protobuf.GeneratedMessageLite;
/** Compile-only public API subset. Numeric enum values are NOT runtime data. */
public final class Protos {
 public enum DisplayType { DISPLAY_TYPE_CLUSTER }
 public enum MediaCodecType { MEDIA_CODEC_VIDEO_H264_BP; public int getNumber(){throw new UnsupportedOperationException();} }
 public enum VideoCodecResolutionType { VIDEO_1280x720, VIDEO_1920x1080 }
 public enum VideoFrameRateType { VIDEO_FPS_30 }
 public static final class VideoConfiguration extends GeneratedMessageLite {
  public static Builder newBuilder(){throw new UnsupportedOperationException();}
  public static final class Builder extends GeneratedMessageLite.Builder {
   public Builder setCodecResolution(VideoCodecResolutionType v){throw new UnsupportedOperationException();}
   public Builder setFrameRate(VideoFrameRateType v){throw new UnsupportedOperationException();}
   public Builder setDensity(int v){throw new UnsupportedOperationException();}
   public Builder setRealDensity(int v){throw new UnsupportedOperationException();}
   public Builder setViewingDistance(int v){throw new UnsupportedOperationException();}
   public Builder setVideoCodecType(MediaCodecType v){throw new UnsupportedOperationException();}
   public Builder setHeightMargin(int v){throw new UnsupportedOperationException();}
  }
 }
}
