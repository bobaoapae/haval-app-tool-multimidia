package br.com.redesurftank.havalshisuku.managers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HotRouterManagerPolicyTest {
    @Test
    public void acceptsOnlyExactAliveStatusWithRealPid() {
        assertTrue(HotRouterManager.isAliveStatusForTest("ALIVE|1234"));
        assertFalse(HotRouterManager.isAliveStatusForTest("ALIVE|1"));
        assertFalse(HotRouterManager.isAliveStatusForTest("NOT_ALIVE|1234"));
        assertFalse(HotRouterManager.isAliveStatusForTest("ALIVE"));
        assertFalse(HotRouterManager.isAliveStatusForTest("DEAD"));
    }
}
