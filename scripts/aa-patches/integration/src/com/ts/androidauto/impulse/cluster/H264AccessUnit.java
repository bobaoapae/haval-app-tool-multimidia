package com.ts.androidauto.impulse.cluster;

import java.nio.ByteBuffer;

/** Bounded, zero-copy Annex-B inspection, not an H.264 decoder. */
public final class H264AccessUnit {
    private H264AccessUnit() {}

    public static boolean hasIdr(ByteBuffer input) {
        return (nalTypes(input) & (1 << 5)) != 0;
    }

    public static boolean hasSpsAndPps(byte[] config) {
        int types = nalTypes(ByteBuffer.wrap(config));
        return (types & (1 << 7)) != 0 && (types & (1 << 8)) != 0;
    }

    public static int nalTypes(ByteBuffer input) {
        int begin = input.position(), end = input.limit();
        if (end - begin <= 0 || end - begin > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("Invalid/beyond-limit AVC access unit");
        }
        int first = findStart(input, begin, end);
        if (first < 0) throw new IllegalArgumentException("Only Annex-B AVC is supported by this adapter");
        for (int i = begin; i < first; i++) {
            if (input.get(i) != 0) throw new IllegalArgumentException("Unexpected prefix before AVC start code");
        }
        int result = 0, count = 0, start = first;
        while (start >= 0) {
            int header = start + (input.get(start + 2) == 1 ? 3 : 4);
            if (header >= end) throw new IllegalArgumentException("Truncated AVC NAL header");
            int value = input.get(header) & 255;
            int type = value & 31;
            if ((value & 128) != 0 || type == 0 || type > 23) {
                throw new IllegalArgumentException("Unsupported/invalid AVC NAL header");
            }
            result |= 1 << type;
            if (++count > 256) throw new IllegalArgumentException("Too many AVC NAL units");
            start = findStart(input, header + 1, end);
        }
        return result;
    }

    private static int findStart(ByteBuffer input, int from, int end) {
        for (int i = from; i + 2 < end; i++) {
            if (input.get(i) == 0 && input.get(i + 1) == 0 &&
                    (input.get(i + 2) == 1 ||
                            (i + 3 < end && input.get(i + 2) == 0 && input.get(i + 3) == 1))) return i;
        }
        return -1;
    }
}
