package com.ofertiber.sarabutton;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BthomeParserTest {
    @Test
    public void parsesOriginalRemoteButtonPacket() {
        byte[] data = bytes(
                0x40,
                0x00, 0x2A,
                0x01, 0x64,
                0x3A, 0x01,
                0x3A, 0x00,
                0x3A, 0x00,
                0x3A, 0x00
        );

        BthomeParser.Packet packet = BthomeParser.parse(data);

        assertTrue(packet.isValidV2());
        assertFalse(packet.isEncrypted());
        assertTrue(packet.isComplete());
        assertEquals(Integer.valueOf(42), packet.getPacketId());
        assertEquals(4, packet.getButtonEvents().size());
        assertEquals(1, packet.getButtonEvents().get(0).getButtonIndex());
        assertEquals(BthomeParser.EVENT_SINGLE_PRESS,
                packet.getButtonEvents().get(0).getEventType());
        assertTrue(packet.looksLikeShellyRcButton4());
    }

    @Test
    public void preservesButtonOrderForFourthButtonLongPress() {
        byte[] data = bytes(
                0x40,
                0x3A, 0x00,
                0x3A, 0x00,
                0x3A, 0x00,
                0x3A, 0x04
        );

        List<BthomeParser.ButtonEvent> events = BthomeParser.parse(data).getButtonEvents();

        assertEquals(4, events.size());
        assertEquals(4, events.get(3).getButtonIndex());
        assertEquals(BthomeParser.EVENT_LONG_PRESS, events.get(3).getEventType());
    }

    @Test
    public void recognizesOriginalAndZigbeeModelIds() {
        BthomeParser.Packet original = BthomeParser.parse(bytes(0x40, 0xF0, 0x07, 0x00));
        BthomeParser.Packet zigbee = BthomeParser.parse(bytes(0x40, 0xF0, 0x16, 0x00));

        assertEquals(Integer.valueOf(0x0007), original.getDeviceType());
        assertEquals(Integer.valueOf(0x0016), zigbee.getDeviceType());
        assertTrue(original.looksLikeShellyRcButton4());
        assertTrue(zigbee.looksLikeShellyRcButton4());
    }

    @Test
    public void parsesToughOneZigbeeDeviceIdAndLongPress() {
        BthomeParser.Packet packet = BthomeParser.parse(bytes(
                0x40,
                0x01, 0x5F,
                0xF0, 0x17, 0x00,
                0x3A, 0x04
        ));

        assertTrue(packet.isComplete());
        assertEquals(Integer.valueOf(0x0017), packet.getDeviceType());
        assertEquals(1, packet.getButtonEvents().size());
        assertEquals(BthomeParser.EVENT_LONG_PRESS,
                packet.getButtonEvents().get(0).getEventType());
    }

    @Test
    public void reportsEncryptedPacketsWithoutParsingCiphertext() {
        BthomeParser.Packet packet = BthomeParser.parse(bytes(0x41, 0x3A, 0x01, 0x44));

        assertTrue(packet.isValidV2());
        assertTrue(packet.isEncrypted());
        assertTrue(packet.getButtonEvents().isEmpty());
        assertNull(packet.getPacketId());
    }

    @Test
    public void rejectsTruncatedObjectWithoutInventingButtonEvent() {
        BthomeParser.Packet packet = BthomeParser.parse(bytes(0x40, 0x00));

        assertTrue(packet.isValidV2());
        assertFalse(packet.isComplete());
        assertTrue(packet.getButtonEvents().isEmpty());
    }

    @Test
    public void namesBothHoldEncodings() {
        assertEquals("החזקה", BthomeParser.eventName(BthomeParser.EVENT_HOLD));
        assertEquals("החזקה", BthomeParser.eventName(BthomeParser.EVENT_LEGACY_HOLD));
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }
}
