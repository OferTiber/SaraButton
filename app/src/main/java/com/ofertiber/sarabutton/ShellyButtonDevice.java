package com.ofertiber.sarabutton;

import java.util.Locale;

/** Identifies the supported Shelly button models from their name or BTHome payload. */
final class ShellyButtonDevice {
    static final int BUTTON_COUNT_UNKNOWN = 0;
    static final int BUTTON_COUNT_TOUGH_1 = 1;
    static final int BUTTON_COUNT_RC_4 = 4;

    private static final int DEVICE_ID_RC_BUTTON_4 = 0x0007;
    private static final int DEVICE_ID_RC_BUTTON_4_ZB = 0x0016;
    private static final int DEVICE_ID_BUTTON_TOUGH_1_ZB = 0x0017;

    private static final String NAME_RC_BUTTON_4 = "SBBT-004CUS";
    private static final String NAME_RC_BUTTON_4_ZB = "SBBT-104CUS";
    private static final String NAME_BUTTON_TOUGH_1_ZB = "SBBT-102C";

    private ShellyButtonDevice() {
    }

    static int identifyButtonCount(String advertisedName, BthomeParser.Packet packet) {
        Integer deviceType = packet == null ? null : packet.getDeviceType();
        if (deviceType != null) {
            if (deviceType == DEVICE_ID_BUTTON_TOUGH_1_ZB) {
                return BUTTON_COUNT_TOUGH_1;
            }
            if (deviceType == DEVICE_ID_RC_BUTTON_4
                    || deviceType == DEVICE_ID_RC_BUTTON_4_ZB) {
                return BUTTON_COUNT_RC_4;
            }
        }

        String normalizedName = advertisedName == null
                ? ""
                : advertisedName.trim().toUpperCase(Locale.ROOT);
        if (normalizedName.startsWith(NAME_BUTTON_TOUGH_1_ZB)) {
            return BUTTON_COUNT_TOUGH_1;
        }
        if (normalizedName.startsWith(NAME_RC_BUTTON_4)
                || normalizedName.startsWith(NAME_RC_BUTTON_4_ZB)) {
            return BUTTON_COUNT_RC_4;
        }

        int eventCount = packet == null ? 0 : packet.getButtonEvents().size();
        if (eventCount == BUTTON_COUNT_TOUGH_1) {
            return BUTTON_COUNT_TOUGH_1;
        }
        if (eventCount >= BUTTON_COUNT_RC_4) {
            return BUTTON_COUNT_RC_4;
        }

        // Retain discovery compatibility with older Shelly advertising names.
        if (normalizedName.startsWith("SBBT")
                || (normalizedName.contains("SHELLY")
                && normalizedName.contains("BUTTON"))) {
            return BUTTON_COUNT_RC_4;
        }
        return BUTTON_COUNT_UNKNOWN;
    }
}
