package br.com.redesurftank.havalshisuku.projectors;

import java.util.Arrays;
import java.util.Random;

/** Exercises the production geometry resolver, with no Android/OEM runtime. */
public final class AaClusterGeometryHarness {
    private static int checks;

    private static void expect(int[] actual, int... expected) {
        checks++;
        if (!Arrays.equals(actual, expected)) {
            throw new AssertionError(Arrays.toString(actual) + " != " + Arrays.toString(expected));
        }
    }

    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("geometry invariant");
    }

    public static void main(String[] args) {
        expect(AaClusterGeometry.resolve(null, null, false), 0, 62, 1920, 658);
        expect(AaClusterGeometry.resolve(null, null, true), 0, 62, 1344, 658);
        int[] theme = {310, 110, 1100, 500};
        expect(AaClusterGeometry.resolve(null, theme, false), 0, 110, 1920, 610);
        expect(AaClusterGeometry.resolve(null, theme, true), 0, 110, 1344, 610);
        for (String absent : new String[] {"", " ", "\t\n"}) {
            expect(AaClusterGeometry.resolve(absent, null, false), 0, 62, 1920, 658);
            expect(AaClusterGeometry.resolve(absent, null, true), 0, 62, 1344, 658);
            expect(AaClusterGeometry.resolve(absent, theme, false), 0, 110, 1920, 610);
            expect(AaClusterGeometry.resolve(absent, theme, true), 0, 110, 1344, 610);
        }
        expect(AaClusterGeometry.resolve("0,62,1920,658", theme, false), 0, 62, 1920, 658);
        expect(AaClusterGeometry.resolve("0,62,1920,658", theme, true), 0, 62, 1344, 658);
        expect(AaClusterGeometry.resolve(" 100, 50, 1900, 700 ", theme, true), 100, 50, 1344, 700);
        expect(AaClusterGeometry.resolve("0,0,2147483647,2147483647", null, false), 0, 0, 1920, 720);
        expect(AaClusterGeometry.resolve("0,0,2147483647,2147483647", null, true), 0, 0, 1344, 720);
        expect(AaClusterGeometry.resolve("1300,100,1500,400", null, true), 1300, 100, 1344, 400);
        // A tiny intersection stays tiny; a hidden or invalid one never reopens 100px.
        expect(AaClusterGeometry.resolve("1343,100,1500,400", null, true), 1343, 100, 1344, 400);
        expect(AaClusterGeometry.resolve("1344,100,1500,400", null, true), 0, 0, 0, 0);
        expect(AaClusterGeometry.resolve("1500,100,1900,400", null, true), 0, 0, 0, 0);
        expect(AaClusterGeometry.resolve("1920,100,2200,400", null, false), 0, 0, 0, 0);
        expect(AaClusterGeometry.resolve("0,720,1920,1000", null, false), 0, 0, 0, 0);
        for (String invalid : new String[] {"1,2,3", "1,2,3,4,", "a,b,c,d", "0,0,99,500",
                "100,0,0,500", "-2147483648,0,2147483647,500", "0,0,2147483648,500",
                "0,0,1920,-2147483648", "0,0,1920,50"}) {
            check(AaClusterGeometry.parseCustom(invalid) == null);
            expect(AaClusterGeometry.resolve(invalid, theme, false), 0, 0, 0, 0);
            expect(AaClusterGeometry.resolve(invalid, theme, true), 0, 0, 0, 0);
        }
        for (int[] invalid : new int[][] {{}, {0, 0, 1920}, {0, 100, 0, 200}, {0, 100, 1920, -1},
                {0, Integer.MAX_VALUE, 1920, Integer.MAX_VALUE}}) {
            expect(AaClusterGeometry.resolve(null, invalid, false), 0, 0, 0, 0);
        }
        expect(AaClusterGeometry.resolve(null, new int[] {0, -100, 1920, 300}, true), 0, 0, 1344, 200);
        expect(AaClusterGeometry.resolve(null, new int[] {0, 100, 1920, Integer.MAX_VALUE}, false), 0, 100, 1920, 720);
        expect(AaClusterGeometry.intersect(null, false), 0, 0, 0, 0);
        expect(AaClusterGeometry.intersect(new int[] {1, 2, 3}, true), 0, 0, 0, 0);
        int[] source = {0, 62, 1920, 658};
        int[] resolved = AaClusterGeometry.intersect(source, true);
        expect(source, 0, 62, 1920, 658);
        resolved[2] = 0;
        expect(AaClusterGeometry.intersect(source, true), 0, 62, 1344, 658);
        Random random = new Random(24L);
        for (int i = 0; i < 5000; i++) {
            int[] bounds = {random.nextInt(4000) - 1000, random.nextInt(2000) - 500,
                    random.nextInt(4000) - 1000, random.nextInt(2000) - 500};
            for (boolean card : new boolean[] {false, true}) {
                int[] actual = AaClusterGeometry.intersect(bounds, card);
                int rightLimit = card ? 1344 : 1920;
                boolean empty = actual[0] == 0 && actual[1] == 0 && actual[2] == 0 && actual[3] == 0;
                check(empty || (actual[0] >= 0 && actual[1] >= 0 && actual[2] <= rightLimit
                        && actual[3] <= 720 && actual[0] < actual[2] && actual[1] < actual[3]));
                expect(AaClusterGeometry.intersect(actual, card), actual);
                if (!empty) {
                    check(actual[0] >= bounds[0] && actual[1] >= bounds[1]
                            && actual[2] <= bounds[2] && actual[3] <= bounds[3]);
                }
            }
        }
        System.out.println("PASS total=" + checks + " production geometry checks");
    }
}
