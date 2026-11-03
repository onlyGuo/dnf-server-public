package com.aiyi.game.dnfserver.channel;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MemoryStats {
    private MemoryStats() { }

    static Map<String, Object> read() {
        long total = -1, available = -1;
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean os = (com.sun.management.OperatingSystemMXBean) bean;
            total = os.getTotalPhysicalMemorySize();
            available = os.getFreePhysicalMemorySize();
        }
        return read(Paths.get("/proc/meminfo"), Paths.get("/sys/fs/cgroup"), total, available);
    }

    static Map<String, Object> read(Path meminfo, Path cgroup, long fallbackTotal, long fallbackAvailable) {
        long total = fallbackTotal, available = fallbackAvailable;
        if (Files.isReadable(meminfo)) {
            try {
                List<String> lines = Files.readAllLines(meminfo, StandardCharsets.US_ASCII);
                long memTotal = meminfoValue(lines, "MemTotal:");
                long memAvailable = meminfoValue(lines, "MemAvailable:");
                if (memTotal > 0 && memAvailable >= 0) { total = memTotal; available = memAvailable; }
            } catch (IOException | NumberFormatException ignored) { }
        }
        long limit = value(cgroup.resolve("memory.max"));
        long current = value(cgroup.resolve("memory.current"));
        if (limit < 0 || current < 0) {
            limit = value(cgroup.resolve("memory/memory.limit_in_bytes"));
            current = value(cgroup.resolve("memory/memory.usage_in_bytes"));
        }
        String scope = "host";
        long used = total >= 0 && available >= 0 ? Math.max(0, total - available) : -1;
        if (limit > 0 && current >= 0 && (total <= 0 || limit < total)) {
            scope = "container";
            total = limit;
            used = Math.min(current, total);
            long remaining = Math.max(0, total - used);
            available = available >= 0 ? Math.min(available, remaining) : remaining;
        }
        if (total <= 0 || available < 0) { total = -1; available = -1; used = -1; }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalBytes", total < 0 ? null : total);
        result.put("usedBytes", used < 0 ? null : used);
        result.put("availableBytes", available < 0 ? null : available);
        result.put("scope", scope);
        result.put("channelEstimateBytes", 1024L * 1024 * 1024);
        return result;
    }

    private static long meminfoValue(List<String> lines, String name) {
        for (String line : lines) if (line.startsWith(name)) {
            String[] fields = line.trim().split("\\s+");
            if (fields.length >= 2) return Math.multiplyExact(Long.parseLong(fields[1]), 1024L);
        }
        return -1;
    }

    private static long value(Path file) {
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.US_ASCII).trim();
            return "max".equals(text) ? -1 : Long.parseLong(text);
        } catch (IOException | NumberFormatException | SecurityException e) { return -1; }
    }
}
