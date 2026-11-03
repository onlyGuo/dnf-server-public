package com.aiyi.game.dnfserver.channel;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.Assert.*;

public class MemoryStatsTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private static final long GIB = 1024L * 1024 * 1024;

    @Test public void usesTheEffectiveContainerLimitAndAvailableHostMemory() throws Exception {
        Path root = temp.newFolder("memory").toPath();
        Path info = root.resolve("meminfo");
        Files.write(info, "MemTotal: 8388608 kB\nMemAvailable: 2097152 kB\n".getBytes(StandardCharsets.US_ASCII));
        Files.write(root.resolve("memory.max"), Long.toString(4 * GIB).getBytes(StandardCharsets.US_ASCII));
        Files.write(root.resolve("memory.current"), Long.toString(3 * GIB).getBytes(StandardCharsets.US_ASCII));
        Map<String, Object> stats = MemoryStats.read(info, root, -1, -1);
        assertEquals(4 * GIB, stats.get("totalBytes"));
        assertEquals(3 * GIB, stats.get("usedBytes"));
        assertEquals(GIB, stats.get("availableBytes"));
        assertEquals("container", stats.get("scope"));
    }

    @Test public void unlimitedContainerUsesHostFigures() throws Exception {
        Path root = temp.newFolder("host").toPath();
        Path info = root.resolve("meminfo");
        Files.write(info, "MemTotal: 8388608 kB\nMemAvailable: 2097152 kB\n".getBytes(StandardCharsets.US_ASCII));
        Files.write(root.resolve("memory.max"), "max".getBytes(StandardCharsets.US_ASCII));
        Map<String, Object> stats = MemoryStats.read(info, root, -1, -1);
        assertEquals(8 * GIB, stats.get("totalBytes"));
        assertEquals(6 * GIB, stats.get("usedBytes"));
        assertEquals(2 * GIB, stats.get("availableBytes"));
        assertEquals(GIB, stats.get("channelEstimateBytes"));
    }

    @Test public void supportsCentosCgroupV1MemoryLimits() throws Exception {
        Path root = temp.newFolder("v1").toPath();
        Path info = root.resolve("meminfo");
        Files.write(info, "MemTotal: 8388608 kB\nMemAvailable: 1048576 kB\n".getBytes(StandardCharsets.US_ASCII));
        Path v1 = Files.createDirectories(root.resolve("memory"));
        Files.write(v1.resolve("memory.limit_in_bytes"), Long.toString(2 * GIB).getBytes(StandardCharsets.US_ASCII));
        Files.write(v1.resolve("memory.usage_in_bytes"), Long.toString(3 * GIB / 2).getBytes(StandardCharsets.US_ASCII));
        Map<String, Object> stats = MemoryStats.read(info, root, -1, -1);
        assertEquals(2 * GIB, stats.get("totalBytes"));
        assertEquals(3 * GIB / 2, stats.get("usedBytes"));
        assertEquals(GIB / 2, stats.get("availableBytes"));
    }
}
