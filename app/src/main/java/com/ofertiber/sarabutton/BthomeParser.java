package com.ofertiber.sarabutton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Parses the BTHome v2 service data emitted by Shelly BLU button remotes. */
public final class BthomeParser {
    public static final int EVENT_NONE = 0x00;
    public static final int EVENT_SINGLE_PRESS = 0x01;
    public static final int EVENT_DOUBLE_PRESS = 0x02;
    public static final int EVENT_TRIPLE_PRESS = 0x03;
    public static final int EVENT_LONG_PRESS = 0x04;
    public static final int EVENT_DOUBLE_LONG_PRESS = 0x05;
    public static final int EVENT_TRIPLE_LONG_PRESS = 0x06;
    public static final int EVENT_HOLD = 0x80;
    public static final int EVENT_LEGACY_HOLD = 0xFE;

    private static final int OBJECT_PACKET_ID = 0x00;
    private static final int OBJECT_BATTERY = 0x01;
    private static final int OBJECT_BUTTON = 0x3A;
    private static final int OBJECT_DEVICE_TYPE = 0xF0;
    private static final int OBJECT_FIRMWARE_32 = 0xF1;
    private static final int OBJECT_FIRMWARE_24 = 0xF2;

    private BthomeParser() {
    }

    public static Packet parse(byte[] serviceData) {
        if (serviceData == null || serviceData.length == 0) {
            return Packet.invalid();
        }

        int deviceInformation = unsigned(serviceData[0]);
        int version = (deviceInformation >> 5) & 0x07;
        boolean encrypted = (deviceInformation & 0x01) != 0;
        if (version != 2 || encrypted) {
            return new Packet(version == 2, encrypted, version, null, null,
                    Collections.emptyList(), false);
        }

        Integer packetId = null;
        Integer deviceType = null;
        List<ButtonEvent> buttonEvents = new ArrayList<>();
        int buttonIndex = 0;
        int offset = 1;
        boolean complete = true;

        while (offset < serviceData.length) {
            int objectId = unsigned(serviceData[offset++]);
            switch (objectId) {
                case OBJECT_PACKET_ID:
                    if (!has(serviceData, offset, 1)) {
                        complete = false;
                        break;
                    }
                    packetId = unsigned(serviceData[offset]);
                    offset += 1;
                    continue;
                case OBJECT_BATTERY:
                    if (!has(serviceData, offset, 1)) {
                        complete = false;
                        break;
                    }
                    offset += 1;
                    continue;
                case OBJECT_BUTTON:
                    if (!has(serviceData, offset, 1)) {
                        complete = false;
                        break;
                    }
                    buttonIndex += 1;
                    buttonEvents.add(new ButtonEvent(buttonIndex, unsigned(serviceData[offset])));
                    offset += 1;
                    continue;
                case OBJECT_DEVICE_TYPE:
                    if (!has(serviceData, offset, 2)) {
                        complete = false;
                        break;
                    }
                    deviceType = littleEndian(serviceData, offset, 2);
                    offset += 2;
                    continue;
                case OBJECT_FIRMWARE_32:
                    if (!has(serviceData, offset, 4)) {
                        complete = false;
                        break;
                    }
                    offset += 4;
                    continue;
                case OBJECT_FIRMWARE_24:
                    if (!has(serviceData, offset, 3)) {
                        complete = false;
                        break;
                    }
                    offset += 3;
                    continue;
                default:
                    // Object sizes are defined by the BTHome registry. Stop instead of
                    // guessing a size and accidentally treating sensor data as a press.
                    complete = false;
                    break;
            }
            break;
        }

        return new Packet(true, false, version, packetId, deviceType,
                Collections.unmodifiableList(buttonEvents), complete);
    }

    public static String eventName(int eventType) {
        switch (eventType) {
            case EVENT_NONE:
                return "שחרור";
            case EVENT_SINGLE_PRESS:
                return "לחיצה אחת";
            case EVENT_DOUBLE_PRESS:
                return "לחיצה כפולה";
            case EVENT_TRIPLE_PRESS:
                return "לחיצה משולשת";
            case EVENT_LONG_PRESS:
                return "לחיצה ארוכה";
            case EVENT_DOUBLE_LONG_PRESS:
                return "שתי לחיצות ארוכות";
            case EVENT_TRIPLE_LONG_PRESS:
                return "שלוש לחיצות ארוכות";
            case EVENT_HOLD:
            case EVENT_LEGACY_HOLD:
                return "החזקה";
            default:
                return "אירוע 0x" + Integer.toHexString(eventType);
        }
    }

    static int eventNameResource(int eventType) {
        switch (eventType) {
            case EVENT_NONE:
                return R.string.event_released;
            case EVENT_SINGLE_PRESS:
                return R.string.press_single;
            case EVENT_DOUBLE_PRESS:
                return R.string.press_double;
            case EVENT_TRIPLE_PRESS:
                return R.string.press_triple;
            case EVENT_LONG_PRESS:
                return R.string.press_long;
            case EVENT_DOUBLE_LONG_PRESS:
                return R.string.press_double_long;
            case EVENT_TRIPLE_LONG_PRESS:
                return R.string.press_triple_long;
            case EVENT_HOLD:
            case EVENT_LEGACY_HOLD:
                return R.string.event_hold;
            default:
                return 0;
        }
    }

    private static boolean has(byte[] data, int offset, int count) {
        return offset >= 0 && count >= 0 && offset + count <= data.length;
    }

    private static int littleEndian(byte[] data, int offset, int count) {
        int value = 0;
        for (int index = 0; index < count; index++) {
            value |= unsigned(data[offset + index]) << (8 * index);
        }
        return value;
    }

    private static int unsigned(byte value) {
        return value & 0xFF;
    }

    public static final class ButtonEvent {
        private final int buttonIndex;
        private final int eventType;

        ButtonEvent(int buttonIndex, int eventType) {
            this.buttonIndex = buttonIndex;
            this.eventType = eventType;
        }

        public int getButtonIndex() {
            return buttonIndex;
        }

        public int getEventType() {
            return eventType;
        }

        public boolean isActive() {
            return eventType != EVENT_NONE;
        }
    }

    public static final class Packet {
        private final boolean validV2;
        private final boolean encrypted;
        private final int version;
        private final Integer packetId;
        private final Integer deviceType;
        private final List<ButtonEvent> buttonEvents;
        private final boolean complete;

        Packet(
                boolean validV2,
                boolean encrypted,
                int version,
                Integer packetId,
                Integer deviceType,
                List<ButtonEvent> buttonEvents,
                boolean complete
        ) {
            this.validV2 = validV2;
            this.encrypted = encrypted;
            this.version = version;
            this.packetId = packetId;
            this.deviceType = deviceType;
            this.buttonEvents = buttonEvents;
            this.complete = complete;
        }

        static Packet invalid() {
            return new Packet(false, false, -1, null, null, Collections.emptyList(), false);
        }

        public boolean isValidV2() {
            return validV2;
        }

        public boolean isEncrypted() {
            return encrypted;
        }

        public int getVersion() {
            return version;
        }

        public Integer getPacketId() {
            return packetId;
        }

        public Integer getDeviceType() {
            return deviceType;
        }

        public List<ButtonEvent> getButtonEvents() {
            return buttonEvents;
        }

        public boolean isComplete() {
            return complete;
        }

        public boolean looksLikeShellyRcButton4() {
            return (deviceType != null && (deviceType == 0x0007 || deviceType == 0x0016))
                    || buttonEvents.size() >= 4;
        }
    }
}
