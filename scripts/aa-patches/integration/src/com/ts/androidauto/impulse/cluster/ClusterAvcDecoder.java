package com.ts.androidauto.impulse.cluster;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import br.com.redesurftank.havalshisuku.api.AaClusterProtocol;
import impulse.cluster.prototype.ClusterFramePump;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Independent Android API-28 MediaCodec adapter; never uses MAIN's VideoPlayer.
 * Construction/consume/close must run off GAL/Binder/UI callback threads.
 * Every instance owns a fresh codec for one Surface generation, and borrows the
 * Surface until close settles. The caller releases its parcelled Surface then.
 * This adapter is compile/assembly checked, not hardware/phone validated.
 */
public final class ClusterAvcDecoder implements ClusterFramePump.Decoder {
    public interface Events {
        void firstFrameRendered(long presentationTimeUs);
        void failed(Throwable failure);
    }
    private final Events events;
    private final HandlerThread callbacks;
    private final ArrayBlockingQueue<Integer> available = new ArrayBlockingQueue<>(32);
    private final AtomicBoolean closing = new AtomicBoolean();
    private final AtomicBoolean rendered = new AtomicBoolean();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private MediaCodec codec;
    private boolean awaitingIdr = true;
    private int framesWaitingForIdr;

    public ClusterAvcDecoder(Surface surface, int width, int height,
                             byte[] codecConfig, Events events) throws Exception {
        if (surface == null || !surface.isValid()) throw new IllegalArgumentException("Valid Surface required");
        if (width != AaClusterProtocol.STREAM_WIDTH || height != AaClusterProtocol.STREAM_HEIGHT) throw new IllegalArgumentException("Unsupported CLUSTER size");
        if (codecConfig == null || codecConfig.length == 0 || codecConfig.length > 65536 ||
                !H264AccessUnit.hasSpsAndPps(codecConfig)) {
            throw new IllegalArgumentException("Annex-B SPS/PPS NAL presence required; MediaCodec validates contents");
        }
        if (events == null) throw new NullPointerException("events");
        this.events = events;
        callbacks = new HandlerThread("Impulse-Cluster-Codec");
        callbacks.start();
        try {
            Handler handler = new Handler(callbacks.getLooper());
            codec = MediaCodec.createDecoderByType("video/avc");
            codec.setCallback(new MediaCodec.Callback() {
                @Override public void onInputBufferAvailable(MediaCodec c, int index) {
                    if (!closing.get() && !available.offer(index)) fail(new IllegalStateException("Codec input-index overflow"));
                }
                @Override public void onOutputBufferAvailable(MediaCodec c, int index, MediaCodec.BufferInfo info) {
                    if (closing.get()) return;
                    try {
                        boolean display = (info.flags & (MediaCodec.BUFFER_FLAG_CODEC_CONFIG | MediaCodec.BUFFER_FLAG_END_OF_STREAM)) == 0;
                        c.releaseOutputBuffer(index, display);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) fail(new IllegalStateException("Unexpected CLUSTER end-of-stream"));
                    } catch (Throwable problem) { if (!closing.get()) fail(problem); }
                }
                @Override public void onError(MediaCodec c, MediaCodec.CodecException error) {
                    if (!closing.get()) fail(error);
                }
                @Override public void onOutputFormatChanged(MediaCodec c, MediaFormat format) {
                    // Output format is informational. The fixed map bounds and
                    // Surface generation are not rewritten by this callback.
                }
            }, handler);
            codec.setOnFrameRenderedListener(new MediaCodec.OnFrameRenderedListener() {
                @Override public void onFrameRendered(MediaCodec c, long presentationTimeUs, long nanoTime) {
                    if (!closing.get() && failure.get() == null && rendered.compareAndSet(false, true)) {
                        try { ClusterAvcDecoder.this.events.firstFrameRendered(presentationTimeUs); }
                        catch (Throwable problem) { fail(problem); }
                    }
                }
            }, handler);
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
            codec.configure(format, surface, null, 0);
            codec.start();
            // Preserve the observed OEM raw codec-config input path, separately
            // from data-frame credit and pool ownership. No synthetic ACK here.
            queue(ByteBuffer.wrap(codecConfig), 0L, MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
        } catch (Throwable problem) {
            try { close(); } catch (Throwable cleanup) { if (cleanup != problem) problem.addSuppressed(cleanup); }
            if (problem instanceof Exception) throw (Exception) problem;
            throw (Error) problem;
        }
    }

    @Override public void consume(int sessionId, long timestamp, ByteBuffer buffer) throws Exception {
        checkHealthy();
        if (awaitingIdr) {
            if (!H264AccessUnit.hasIdr(buffer)) {
                if (++framesWaitingForIdr > 120) throw new IllegalStateException("No IDR after CLUSTER codec restart");
                return; // Deliberate recovery discard; caller ACKs/recycles once.
            }
            awaitingIdr = false;
        }
        // Match the observed OEM presentation-time source. Phone timestamp
        // units are not independently established by the receiver artifacts.
        queue(buffer.duplicate(), android.os.SystemClock.elapsedRealtime() * 1000L, 0);
    }

    private void queue(ByteBuffer source, long timestamp, int flags) throws Exception {
        checkHealthy();
        Integer index = available.poll(750, TimeUnit.MILLISECONDS);
        checkHealthy();
        if (index == null || index < 0) throw new IllegalStateException("CLUSTER decoder input timed out");
        ByteBuffer target = codec.getInputBuffer(index);
        if (target == null) throw new IllegalStateException("Codec input buffer missing");
        target.clear();
        int bytes = source.remaining();
        if (bytes > target.remaining()) throw new IllegalStateException("Access unit exceeds codec input capacity; no guessed fragmentation");
        target.put(source);
        codec.queueInputBuffer(index, 0, bytes, timestamp, flags);
    }

    private void checkHealthy() throws Exception {
        Throwable problem = failure.get();
        if (closing.get()) throw new IllegalStateException("CLUSTER decoder closed");
        if (problem != null) throw new IllegalStateException("CLUSTER codec failed", problem);
    }

    private void fail(Throwable problem) {
        if (!failure.compareAndSet(null, problem)) return;
        available.offer(-1);
        try { events.failed(problem); } catch (Throwable ignored) { /* Original failure retained. */ }
    }

    @Override public void close() throws Exception {
        if (!closing.compareAndSet(false, true)) return;
        available.offer(-1);
        Throwable problem = null;
        if (codec != null) {
            try { codec.stop(); } catch (Throwable stopped) { problem = stopped; }
            try { codec.release(); } catch (Throwable released) {
                if (problem == null) problem = released;
                else if (problem != released) problem.addSuppressed(released);
            }
        }
        callbacks.quitSafely();
        if (Thread.currentThread() != callbacks) {
            callbacks.join(1500);
            if (callbacks.isAlive() && problem == null) problem = new IllegalStateException("Codec callback thread did not stop");
        }
        available.clear();
        if (problem instanceof Exception) throw (Exception) problem;
        if (problem != null) throw (Error) problem;
    }
}
