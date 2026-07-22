package com.ofertiber.sarabutton;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BleScanManagerTest {
    @Test
    public void persistentScanRequiresAndroidEight() {
        assertFalse(BleScanManager.supportsPersistentScan(23));
        assertFalse(BleScanManager.supportsPersistentScan(25));
        assertTrue(BleScanManager.supportsPersistentScan(26));
        assertTrue(BleScanManager.supportsPersistentScan(35));
    }
}
