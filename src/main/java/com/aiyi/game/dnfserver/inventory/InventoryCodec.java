package com.aiyi.game.dnfserver.inventory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** The game's four-byte prefix is opaque; each decompressed slot is exactly 61 bytes. */
public final class InventoryCodec {
    public static final int SLOT_SIZE = 61;
    private static final int MAX_SIZE = 4 * 1024 * 1024;
    private final byte[] prefix;
    private final byte[] slots;

    private InventoryCodec(byte[] prefix, byte[] slots) { this.prefix = prefix; this.slots = slots; }

    public static InventoryCodec read(byte[] blob) throws IOException {
        if (blob == null || blob.length < 5) throw new IllegalArgumentException("背包数据为空或格式不支持");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(blob, 4, blob.length - 4))) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > MAX_SIZE) throw new IllegalArgumentException("背包数据过大");
                output.write(buffer, 0, count);
            }
        }
        byte[] slots = output.toByteArray();
        if (slots.length % SLOT_SIZE != 0) throw new IllegalArgumentException("背包槽位数据长度不正确");
        return new InventoryCodec(Arrays.copyOf(blob, 4), slots);
    }

    public int size() { return slots.length / SLOT_SIZE; }

    public Map<String, Object> slot(int index) {
        check(index); int p = index * SLOT_SIZE;
        int type = u8(p + 1);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("index", index); item.put("type", type); item.put("id", u32(p + 2, ByteOrder.LITTLE_ENDIAN));
        item.put("seal", u8(p)); item.put("enhancement", u8(p + 6) & 31); item.put("sealCount", u8(p + 6) >>> 5);
        item.put("amountOrGrade", u32(p + 7, type == 1 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN));
        item.put("durability", u16(p + 11)); item.put("orb", u32(p + 13, ByteOrder.LITTLE_ENDIAN));
        item.put("increaseType", u8(p + 17)); item.put("increaseValue", u16(p + 18)); item.put("forge", u8(p + 51));
        return item;
    }

    public void swap(int from, int to) {
        check(from); check(to);
        if (from == to) return;
        int a = from * SLOT_SIZE, b = to * SLOT_SIZE;
        byte[] temp = Arrays.copyOfRange(slots, a, a + SLOT_SIZE);
        System.arraycopy(slots, b, slots, a, SLOT_SIZE);
        System.arraycopy(temp, 0, slots, b, SLOT_SIZE);
    }

    public void clear(int index) { check(index); Arrays.fill(slots, index * SLOT_SIZE, (index + 1) * SLOT_SIZE, (byte) 0); }

    public void edit(int index, Map<String, Object> changes) {
        check(index); int p = index * SLOT_SIZE;
        if (changes.containsKey("type")) put8(p + 1, number(changes, "type", 255));
        int type = u8(p + 1);
        if (changes.containsKey("id")) put32(p + 2, number(changes, "id", 0xffffffffL), ByteOrder.LITTLE_ENDIAN);
        if (changes.containsKey("seal")) put8(p, number(changes, "seal", 255));
        int enhance = changes.containsKey("enhancement") ? (int) number(changes, "enhancement", 31) : u8(p + 6) & 31;
        int sealCount = changes.containsKey("sealCount") ? (int) number(changes, "sealCount", 7) : u8(p + 6) >>> 5;
        put8(p + 6, enhance | sealCount << 5);
        if (changes.containsKey("amountOrGrade")) put32(p + 7, number(changes, "amountOrGrade", 0xffffffffL), type == 1 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        if (changes.containsKey("durability")) put16(p + 11, number(changes, "durability", 65535));
        if (changes.containsKey("orb")) put32(p + 13, number(changes, "orb", 0xffffffffL), ByteOrder.LITTLE_ENDIAN);
        if (changes.containsKey("increaseType")) put8(p + 17, number(changes, "increaseType", 255));
        if (changes.containsKey("increaseValue")) put16(p + 18, number(changes, "increaseValue", 65535));
        if (changes.containsKey("forge")) put8(p + 51, number(changes, "forge", 255));
    }

    public byte[] write() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); output.write(prefix);
        try (DeflaterOutputStream compressed = new DeflaterOutputStream(output)) { compressed.write(slots); }
        return output.toByteArray();
    }

    private long number(Map<String, Object> values, String key, long max) {
        Object value = values.get(key);
        try {
            long n = Long.parseLong(String.valueOf(value));
            if (n < 0 || n > max) throw new IllegalArgumentException(key + " 超出范围");
            return n;
        } catch (NumberFormatException e) { throw new IllegalArgumentException(key + " 必须是整数"); }
    }
    private void check(int index) { if (index < 0 || index >= size()) throw new IllegalArgumentException("背包位置不存在"); }
    private int u8(int p) { return slots[p] & 255; }
    private int u16(int p) { return Short.toUnsignedInt(ByteBuffer.wrap(slots, p, 2).order(ByteOrder.LITTLE_ENDIAN).getShort()); }
    private long u32(int p, ByteOrder order) { return Integer.toUnsignedLong(ByteBuffer.wrap(slots, p, 4).order(order).getInt()); }
    private void put8(int p, long v) { slots[p] = (byte) v; }
    private void put16(int p, long v) { ByteBuffer.wrap(slots, p, 2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) v); }
    private void put32(int p, long v, ByteOrder order) { ByteBuffer.wrap(slots, p, 4).order(order).putInt((int) v); }
}
