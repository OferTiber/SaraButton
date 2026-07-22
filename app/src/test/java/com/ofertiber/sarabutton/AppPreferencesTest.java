package com.ofertiber.sarabutton;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AppPreferencesTest {
    @Test
    public void usesIndependentPhoneKeysForAllFourButtons() {
        assertEquals("button_phone_1", AppPreferences.buttonPhoneKey(1));
        assertEquals("button_phone_2", AppPreferences.buttonPhoneKey(2));
        assertEquals("button_phone_3", AppPreferences.buttonPhoneKey(3));
        assertEquals("button_phone_4", AppPreferences.buttonPhoneKey(4));
    }

    @Test
    public void usesIndependentDeduplicationKeysForEachButton() {
        assertEquals(
                "last_event_fingerprint_button_1",
                AppPreferences.lastEventFingerprintKey(1)
        );
        assertEquals(
                "last_event_fingerprint_button_4",
                AppPreferences.lastEventFingerprintKey(4)
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsPhoneKeyOutsidePhysicalButtonRange() {
        AppPreferences.buttonPhoneKey(5);
    }
}
