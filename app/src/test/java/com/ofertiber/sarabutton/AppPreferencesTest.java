package com.ofertiber.sarabutton;

import android.content.SharedPreferences;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class AppPreferencesTest {
    @Test
    public void usesIndependentPhoneKeysForAllFourButtons() {
        assertEquals("panel_button_phone_1", AppPreferences.panelButtonPhoneKey(1));
        assertEquals("panel_button_phone_2", AppPreferences.panelButtonPhoneKey(2));
        assertEquals("panel_button_phone_3", AppPreferences.panelButtonPhoneKey(3));
        assertEquals("panel_button_phone_4", AppPreferences.panelButtonPhoneKey(4));
        assertEquals("tough_button_phone", AppPreferences.KEY_TOUGH_PHONE);
    }

    @Test
    public void usesIndependentDeduplicationKeysForEachRemoteAndButton() {
        assertEquals(
                "last_event_fingerprint_panel_button_1",
                AppPreferences.lastEventFingerprintKey(
                        ShellyButtonDevice.BUTTON_COUNT_RC_4,
                        1
                )
        );
        assertEquals(
                "last_event_fingerprint_panel_button_4",
                AppPreferences.lastEventFingerprintKey(
                        ShellyButtonDevice.BUTTON_COUNT_RC_4,
                        4
                )
        );
        assertEquals(
                "last_event_fingerprint_tough_button_1",
                AppPreferences.lastEventFingerprintKey(
                        ShellyButtonDevice.BUTTON_COUNT_TOUGH_1,
                        1
                )
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsPhoneKeyOutsidePhysicalButtonRange() {
        AppPreferences.panelButtonPhoneKey(5);
    }

    @Test
    public void resolvesEachConfiguredAddressToItsRemoteProfile() {
        String panelAddress = "C0:2C:ED:E8:18:30";
        String toughAddress = "A4:C1:38:00:11:22";

        assertEquals(
                ShellyButtonDevice.BUTTON_COUNT_RC_4,
                AppPreferences.getRemoteButtonCountForAddress(
                        panelAddress,
                        toughAddress,
                        panelAddress.toLowerCase()
                )
        );
        assertEquals(
                ShellyButtonDevice.BUTTON_COUNT_TOUGH_1,
                AppPreferences.getRemoteButtonCountForAddress(
                        panelAddress,
                        toughAddress,
                        toughAddress.toLowerCase()
                )
        );
        assertEquals(
                ShellyButtonDevice.BUTTON_COUNT_UNKNOWN,
                AppPreferences.getRemoteButtonCountForAddress(
                        panelAddress,
                        toughAddress,
                        "00:00:00:00:00:00"
                )
        );
    }

    @Test
    public void storesRemoteTypesInIndependentSlots() {
        assertEquals(
                AppPreferences.KEY_PANEL_REMOTE_ADDRESS,
                AppPreferences.remoteAddressKey(ShellyButtonDevice.BUTTON_COUNT_RC_4)
        );
        assertEquals(
                AppPreferences.KEY_TOUGH_REMOTE_ADDRESS,
                AppPreferences.remoteAddressKey(ShellyButtonDevice.BUTTON_COUNT_TOUGH_1)
        );
    }

    @Test
    public void routesPanelAndToughPhonesIndependently() {
        Map<String, Object> values = new HashMap<>();
        values.put(AppPreferences.panelButtonPhoneKey(1), "+111111");
        values.put(AppPreferences.panelButtonPhoneKey(2), "+222222");
        values.put(AppPreferences.KEY_TOUGH_PHONE, "+999999");
        SharedPreferences preferences = preferences(values);

        assertEquals(
                "+222222",
                AppPreferences.getPhoneForRemote(
                        preferences,
                        ShellyButtonDevice.BUTTON_COUNT_RC_4,
                        2
                )
        );
        assertEquals(
                "+999999",
                AppPreferences.getPhoneForRemote(
                        preferences,
                        ShellyButtonDevice.BUTTON_COUNT_TOUGH_1,
                        1
                )
        );
        assertEquals(
                "",
                AppPreferences.getPhoneForRemote(
                        preferences,
                        ShellyButtonDevice.BUTTON_COUNT_TOUGH_1,
                        2
                )
        );

        values.put(AppPreferences.KEY_USE_FIRST_FOR_ALL, true);
        assertEquals(
                "+111111",
                AppPreferences.getPhoneForRemote(
                        preferences,
                        ShellyButtonDevice.BUTTON_COUNT_RC_4,
                        2
                )
        );
        assertEquals(
                "+999999",
                AppPreferences.getPhoneForRemote(
                        preferences,
                        ShellyButtonDevice.BUTTON_COUNT_TOUGH_1,
                        1
                )
        );
    }

    @Test
    public void migratesExistingToughSelectionAndItsPhone() {
        Map<String, Object> values = new HashMap<>();
        values.put(AppPreferences.KEY_REMOTE_ADDRESS, "A4:C1:38:00:11:22");
        values.put(AppPreferences.KEY_REMOTE_NAME, "SBBT-102C-A1B2");
        values.put(
                AppPreferences.KEY_REMOTE_BUTTON_COUNT,
                ShellyButtonDevice.BUTTON_COUNT_TOUGH_1
        );
        values.put("button_phone_1", "+972501234567");
        SharedPreferences preferences = preferences(values);

        AppPreferences.migrateMultiRemotePreferences(preferences);

        assertEquals(
                "A4:C1:38:00:11:22",
                AppPreferences.getRemoteAddress(
                        preferences,
                        ShellyButtonDevice.BUTTON_COUNT_TOUGH_1
                )
        );
        assertEquals("+972501234567", AppPreferences.getToughPhone(preferences));
        assertFalse(AppPreferences.hasRemote(
                preferences,
                ShellyButtonDevice.BUTTON_COUNT_RC_4
        ));
        assertEquals("", AppPreferences.getIndividuallyAssignedPanelPhone(preferences, 1));
    }

    @Test
    public void migratesExistingPanelSelectionAndAllFourPhones() {
        Map<String, Object> values = new HashMap<>();
        values.put(AppPreferences.KEY_REMOTE_ADDRESS, "C0:2C:ED:E8:18:30");
        values.put(AppPreferences.KEY_REMOTE_NAME, "SBBT-104CUS-A1B2");
        values.put(
                AppPreferences.KEY_REMOTE_BUTTON_COUNT,
                ShellyButtonDevice.BUTTON_COUNT_RC_4
        );
        for (int button = 1; button <= 4; button++) {
            values.put("button_phone_" + button, "+97250000000" + button);
        }
        SharedPreferences preferences = preferences(values);

        AppPreferences.migrateMultiRemotePreferences(preferences);

        assertEquals(
                "C0:2C:ED:E8:18:30",
                AppPreferences.getRemoteAddress(
                        preferences,
                        ShellyButtonDevice.BUTTON_COUNT_RC_4
                )
        );
        for (int button = 1; button <= 4; button++) {
            assertEquals(
                    "+97250000000" + button,
                    AppPreferences.getIndividuallyAssignedPanelPhone(
                            preferences,
                            button
                    )
            );
        }
        assertFalse(AppPreferences.hasRemote(
                preferences,
                ShellyButtonDevice.BUTTON_COUNT_TOUGH_1
        ));
        assertEquals("", AppPreferences.getToughPhone(preferences));
    }

    private static SharedPreferences preferences(Map<String, Object> values) {
        return (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class},
                (proxy, method, arguments) -> {
                    String name = method.getName();
                    if ("getString".equals(name)
                            || "getBoolean".equals(name)
                            || "getInt".equals(name)
                            || "getLong".equals(name)
                            || "getFloat".equals(name)) {
                        return values.getOrDefault(arguments[0], arguments[1]);
                    }
                    if ("contains".equals(name)) {
                        return values.containsKey(arguments[0]);
                    }
                    if ("getAll".equals(name)) {
                        return new HashMap<>(values);
                    }
                    if ("edit".equals(name)) {
                        return editor(values);
                    }
                    throw new UnsupportedOperationException(name);
                }
        );
    }

    private static SharedPreferences.Editor editor(Map<String, Object> values) {
        return (SharedPreferences.Editor) Proxy.newProxyInstance(
                SharedPreferences.Editor.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.Editor.class},
                (proxy, method, arguments) -> {
                    String name = method.getName();
                    if (name.startsWith("put")) {
                        values.put((String) arguments[0], arguments[1]);
                        return proxy;
                    }
                    if ("remove".equals(name)) {
                        values.remove(arguments[0]);
                        return proxy;
                    }
                    if ("clear".equals(name)) {
                        values.clear();
                        return proxy;
                    }
                    if ("commit".equals(name)) {
                        return true;
                    }
                    if ("apply".equals(name)) {
                        return null;
                    }
                    throw new UnsupportedOperationException(name);
                }
        );
    }
}
