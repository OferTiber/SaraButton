package com.ofertiber.sarabutton;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ShellyButtonDeviceTest {
    @Test
    public void identifiesBothZigbeeModelsByOfficialDeviceId() {
        BthomeParser.Packet rcButton4 = BthomeParser.parse(bytes(
                0x40,
                0xF0, 0x16, 0x00
        ));
        BthomeParser.Packet tough1 = BthomeParser.parse(bytes(
                0x40,
                0xF0, 0x17, 0x00
        ));

        assertEquals(
                ShellyButtonDevice.BUTTON_COUNT_RC_4,
                ShellyButtonDevice.identifyButtonCount("", rcButton4)
        );
        assertEquals(
                ShellyButtonDevice.BUTTON_COUNT_TOUGH_1,
                ShellyButtonDevice.identifyButtonCount("", tough1)
        );
    }

    @Test
    public void identifiesZigbeeModelsByNamesWithMacSuffixes() {
        BthomeParser.Packet noPacket = BthomeParser.parse(null);

        assertEquals(
                ShellyButtonDevice.BUTTON_COUNT_RC_4,
                ShellyButtonDevice.identifyButtonCount("SBBT-104CUS-A1B2", noPacket)
        );
        assertEquals(
                ShellyButtonDevice.BUTTON_COUNT_TOUGH_1,
                ShellyButtonDevice.identifyButtonCount("SBBT-102C-A1B2", noPacket)
        );
    }

    @Test
    public void identifiesToughPressPacketWhenNameIsInSeparateAdvertisement() {
        BthomeParser.Packet packet = BthomeParser.parse(bytes(
                0x40,
                0x01, 0x64,
                0x3A, 0x02
        ));

        assertEquals(1, packet.getButtonEvents().size());
        assertEquals(BthomeParser.EVENT_DOUBLE_PRESS,
                packet.getButtonEvents().get(0).getEventType());
        assertEquals(
                ShellyButtonDevice.BUTTON_COUNT_TOUGH_1,
                ShellyButtonDevice.identifyButtonCount("", packet)
        );
    }

    @Test
    public void rejectsUnrelatedBthomeBeacon() {
        BthomeParser.Packet packet = BthomeParser.parse(bytes(0x40, 0x01, 0x64));

        assertEquals(
                ShellyButtonDevice.BUTTON_COUNT_UNKNOWN,
                ShellyButtonDevice.identifyButtonCount("Other sensor", packet)
        );
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }
}
