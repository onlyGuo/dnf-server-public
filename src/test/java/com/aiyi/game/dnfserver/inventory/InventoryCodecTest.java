package com.aiyi.game.dnfserver.inventory;

import org.junit.Test;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

public class InventoryCodecTest {
    private static byte[] blob(byte[] raw) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); out.write(new byte[]{1, 2, 3, 4});
        try (DeflaterOutputStream z = new DeflaterOutputStream(out)) { z.write(raw); }
        return out.toByteArray();
    }
    private static byte[] unpack(byte[] blob) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InflaterInputStream z = new InflaterInputStream(new ByteArrayInputStream(blob, 4, blob.length - 4))) {
            byte[] buffer = new byte[128]; int n; while ((n = z.read(buffer)) != -1) out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
    @Test public void keepsOpaqueBytesAndEquipmentGradeByteOrder() throws Exception {
        byte[] raw = new byte[61 * 2]; raw[1] = 1; raw[2] = 7; raw[7] = 1; raw[10] = 2; raw[33] = 99; raw[60] = 42;
        InventoryCodec codec = InventoryCodec.read(blob(raw));
        assertEquals(16777218L, codec.slot(0).get("amountOrGrade"));
        Map<String, Object> changes = new HashMap<>(); changes.put("enhancement", 17); changes.put("amountOrGrade", 0x12345678L);
        codec.edit(0, changes); codec.swap(0, 1);
        byte[] result = codec.write();
        assertArrayEquals(new byte[]{1, 2, 3, 4}, Arrays.copyOf(result, 4));
        byte[] bytes = unpack(result);
        assertEquals(99, bytes[61 + 33]); assertEquals(42, bytes[61 + 60]);
        assertEquals(0x12, bytes[61 + 7]); assertEquals(0x78, bytes[61 + 10]);
        assertEquals(17, codec.slot(1).get("enhancement"));
    }
    @Test public void stackCountUsesLittleEndianAndRejectsCorruptData() throws Exception {
        byte[] raw = new byte[61]; raw[1] = 2; raw[7] = 3; raw[8] = 1;
        InventoryCodec codec = InventoryCodec.read(blob(raw));
        assertEquals(259L, codec.slot(0).get("amountOrGrade"));
        try { InventoryCodec.read(blob(new byte[62])); fail("Expected invalid slot length"); }
        catch (IllegalArgumentException expected) { }
        try { codec.edit(0, Collections.singletonMap("enhancement", 32)); fail("Expected range error"); }
        catch (IllegalArgumentException expected) { }
    }
}
