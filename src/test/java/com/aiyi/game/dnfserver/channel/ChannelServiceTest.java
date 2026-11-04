package com.aiyi.game.dnfserver.channel;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class ChannelServiceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Path root;
    private Path log;
    private Path game;
    private Path fakeProc;
    private Path executable;
    private Path supervisorState;
    private DataSource db;
    private ChannelService service;

    @Before public void setUp() throws Exception {
        root = temp.newFolder("dnf").toPath();
        game = temp.newFolder("game").toPath();
        Files.write(game.resolve("df_game_r"), new byte[0]);
        Files.createDirectories(root.resolve("run"));
        Files.write(root.resolve("run/start_game.sh"), ("#!/bin/bash\n# DNF_CHANNEL_MANAGER_IP_OVERRIDE\n").getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(root.resolve("monitor_ip"));
        Files.write(root.resolve("monitor_ip/MONITOR_PUBLIC_IP"), "192.0.2.1\n".getBytes(StandardCharsets.UTF_8));
        Path state = temp.newFolder("supervisor-state").toPath();
        supervisorState = state;
        log = temp.getRoot().toPath().resolve("operations.log");
        executable = temp.getRoot().toPath().resolve("supervisorctl");
        String script = "#!/bin/sh\nset -eu\nstate='" + state + "'\nlog='" + log + "'\n" +
                "cmd=$1\nname=${2:-}\n" +
                "if [ \"$cmd\" = pid ]; then if [ -f \"$state/pid-$name\" ]; then cat \"$state/pid-$name\"; else echo 0; fi; exit 0; fi\n" +
                "if [ \"$cmd\" = status ]; then if [ -z \"$name\" ]; then echo 'dnf_channel:channel RUNNING'; for f in \"$state\"/*; do [ -f \"$f\" ] || continue; echo \"$(basename \"$f\") $(cat \"$f\")\"; done; exit 0; fi; if [ -f \"$state/$name\" ]; then echo \"$name $(cat \"$state/$name\")\"; else echo \"$name: ERROR (no such process)\"; exit 3; fi; exit 0; fi\n" +
                "echo \"$cmd $name\" >> \"$log\"\n" +
                "case \"$cmd\" in add|start|restart) echo RUNNING > \"$state/$name\";; stop) echo STOPPED > \"$state/$name\";; esac\n";
        Files.write(executable, script.getBytes(StandardCharsets.UTF_8));
        assertTrue(executable.toFile().setExecutable(true));
        db = new DataSource() {
            public Connection getConnection() throws SQLException { throw new SQLException("no database in test"); }
            public Connection getConnection(String u, String p) throws SQLException { return getConnection(); }
            public PrintWriter getLogWriter() { return null; }
            public void setLogWriter(PrintWriter out) { }
            public void setLoginTimeout(int seconds) { }
            public int getLoginTimeout() { return 0; }
            public Logger getParentLogger() { return Logger.getGlobal(); }
            public <T> T unwrap(Class<T> type) throws SQLException { throw new SQLException("not a wrapper"); }
            public boolean isWrapperFor(Class<?> type) { return false; }
        };
        fakeProc = temp.newFolder("proc").toPath();
        service = new ChannelService("auto", root.toString(), game.toString(), 3, executable.toString(), temp.getRoot().toPath().resolve("monitor_ip.sh").toString(), "cain", fakeProc.toString(), db);
    }

    private ChannelSettings settings(boolean enabled, String mode, String target) {
        ChannelSettings s = new ChannelSettings();
        s.setEnabled(enabled); s.setMode(mode); s.setTarget(target); return s;
    }
    private void clearLog() throws Exception { Files.write(log, new byte[0]); }
    private List<String> operations() throws Exception { return Files.readAllLines(log, StandardCharsets.UTF_8); }

    @Test public void keyInstallationRestartsOnlyRunningGameChannels() throws Exception {
        Files.write(supervisorState.resolve("dnf_channel:game_siroco11"), "RUNNING\n".getBytes(StandardCharsets.UTF_8));
        Files.write(supervisorState.resolve("channel_admin_53"), "RUNNING\n".getBytes(StandardCharsets.UTF_8));
        Files.write(supervisorState.resolve("channel_admin_54"), "STOPPED\n".getBytes(StandardCharsets.UTF_8));
        Files.write(supervisorState.resolve("dnf:bridge"), "RUNNING\n".getBytes(StandardCharsets.UTF_8));
        Path installed = root.resolve("keys-installed");

        Map<String, Object> result = service.installKeyAndRestart(() -> Files.createFile(installed));

        assertTrue(Files.exists(installed));
        assertEquals(2, result.get("runningCount"));
        assertTrue(((List<?>) result.get("failed")).isEmpty());
        assertEquals(2, operations().size());
        assertTrue(operations().contains("restart dnf_channel:game_siroco11"));
        assertTrue(operations().contains("restart channel_admin_53"));
    }

    @Test public void externalGlobalIpChangeUpdatesSharedAddressesForAllChannels() throws Exception {
        service.save(53, settings(true, "INHERIT", ""));
        service.save(54, settings(true, "STATIC", "198.51.100.4"));
        clearLog();
        Files.write(root.resolve("monitor_ip/MONITOR_PUBLIC_IP"), "192.0.2.2\n".getBytes(StandardCharsets.UTF_8));
        service.refreshAddresses();
        assertEquals(2, operations().size());
        assertEquals("restart channel_admin_53", operations().get(0));
        assertEquals("restart channel_admin_54", operations().get(1));
        assertEquals("198.51.100.4", new String(Files.readAllBytes(root.resolve("channel-manager/ips/54")), StandardCharsets.UTF_8).trim());
    }

    @Test public void ddnsChangeRestartsOnlyItsChannelAndKeepsLastAddressOnFailure() throws Exception {
        service.save(53, settings(true, "DDNS", "localhost"));
        service.save(54, settings(true, "STATIC", "198.51.100.4"));
        Files.write(root.resolve("channel-manager/ips/53"), "127.0.0.2\n".getBytes(StandardCharsets.UTF_8));
        clearLog();
        service.refreshAddresses();
        assertEquals(1, operations().size());
        assertEquals("restart channel_admin_53", operations().get(0));
        assertEquals("127.0.0.1", new String(Files.readAllBytes(root.resolve("channel-manager/ips/53")), StandardCharsets.UTF_8).trim());
        Files.write(root.resolve("channel-manager/channels/53.properties"), "mode=DDNS\ntarget=invalid/domain\nenabled=true\ntype=\n".getBytes(StandardCharsets.UTF_8));
        clearLog();
        service.refreshAddresses();
        assertTrue(operations().isEmpty());
        assertEquals("127.0.0.1", new String(Files.readAllBytes(root.resolve("channel-manager/ips/53")), StandardCharsets.UTF_8).trim());
    }

    @Test public void disableWorksWhenDefaultAddressIsUnavailable() throws Exception {
        service.save(53, settings(true, "INHERIT", ""));
        Files.delete(root.resolve("monitor_ip/MONITOR_PUBLIC_IP"));
        clearLog();
        service.save(53, settings(false, "INHERIT", ""));
        assertEquals("stop channel_admin_53", operations().get(0));
    }

    @Test public void statusChangeKeepsAddressAndDoesNotTouchOtherChannels() throws Exception {
        service.save(53, settings(true, "STATIC", "198.51.100.7"));
        service.save(54, settings(true, "STATIC", "198.51.100.8"));
        clearLog();
        service.setEnabled(53, false);
        service.setEnabled(53, true);
        assertEquals(java.util.Arrays.asList("stop channel_admin_53", "start channel_admin_53"), operations());
        String saved = new String(Files.readAllBytes(root.resolve("channel-manager/channels/53.properties")), StandardCharsets.UTF_8);
        assertTrue(saved.contains("mode=STATIC\n"));
        assertTrue(saved.contains("target=198.51.100.7\n"));
        assertTrue(saved.contains("type=\n"));
        clearLog();
        service.setEnabled(53, true);
        assertTrue(operations().isEmpty());
    }

    @Test public void statusChangePreservesAnExplicitChannelType() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(mock(ResultSet.class));
        ChannelService channels = new ChannelService("auto", root.toString(), game.toString(), 3,
                executable.toString(), temp.getRoot().toPath().resolve("monitor_ip.sh").toString(), "cain", fakeProc.toString(), source);
        Files.createDirectories(root.resolve("channel-manager/channels"));
        Files.write(root.resolve("channel-manager/channels/53.properties"),
                "mode=STATIC\ntarget=198.51.100.7\nenabled=true\ntype=15\n".getBytes(StandardCharsets.UTF_8));
        Files.write(supervisorState.resolve("channel_admin_53"), "RUNNING\n".getBytes(StandardCharsets.UTF_8));
        channels.setEnabled(53, false);
        channels.setEnabled(53, true);
        String saved = new String(Files.readAllBytes(root.resolve("channel-manager/channels/53.properties")), StandardCharsets.UTF_8);
        assertTrue(saved.contains("type=15\n"));
        assertTrue(saved.contains("target=198.51.100.7\n"));
        verify(statement).setInt(1, 15);
    }

    @Test public void statusChangeSupportsExistingLegacyChannels() throws Exception {
        Files.write(supervisorState.resolve("dnf_channel:game_siroco53"), "RUNNING\n".getBytes(StandardCharsets.UTF_8));
        service.setEnabled(53, false);
        service.setEnabled(53, true);
        assertEquals(java.util.Arrays.asList("stop dnf_channel:game_siroco53", "start dnf_channel:game_siroco53"), operations());
        assertFalse(Files.exists(root.resolve("conf.d/channel-admin-53.conf")));
    }

    @Test public void statusChangeRejectsMissingChannels() throws Exception {
        try { service.setEnabled(53, true); fail(); }
        catch (IllegalArgumentException expected) { }
        assertFalse(Files.exists(root.resolve("channel-manager/channels/53.properties")));
    }

    @Test public void validationRejectsUnsupportedChannelsAndInvalidAddresses() throws Exception {
        try { service.save(50, settings(true, "INHERIT", "")); fail(); } catch (IllegalArgumentException expected) { }
        try { service.save(53, settings(true, "STATIC", "256.1.2.3")); fail(); } catch (IllegalArgumentException expected) { }
        try { service.save(53, settings(true, "DDNS", "example.com;reboot")); fail(); } catch (IllegalArgumentException expected) { }
        assertFalse(Files.exists(root.resolve("channel-manager/channels/53.properties")));
    }

    @Test public void onlyListeningSocketsCountAsReady() {
        assertTrue(ChannelService.isListeningLine("  7: 00000000:7555 00000000:0000 0A 0:0 00:0", 30037));
        assertFalse(ChannelService.isListeningLine("  7: 00000000:7555 00000000:0000 01 0:0 00:0", 30037));
        assertFalse(ChannelService.isListeningLine("  7: 00000000:7555 00000000:0000 0A 0:0 00:0", 30038));
        assertFalse(ChannelService.isListeningLine("malformed", 30037));
    }

    @Test public void gameProcessMatchingIsScopedToTheExactChannel() {
        assertTrue(ChannelService.isGameCommand("./df_game_r\0siroco56\0start\0".getBytes(StandardCharsets.UTF_8), "siroco", "56"));
        assertFalse(ChannelService.isGameCommand("./df_game_r\0siroco55\0start\0".getBytes(StandardCharsets.UTF_8), "siroco", "56"));
        assertFalse(ChannelService.isGameCommand("./different\0siroco56\0start\0".getBytes(StandardCharsets.UTF_8), "siroco", "56"));
        assertFalse(ChannelService.isGameCommand("./df_game_r\0siroco56\0stop\0".getBytes(StandardCharsets.UTF_8), "siroco", "56"));
    }

    @Test public void automaticallyDetectsAndLosesTheGameEnvironment() throws Exception {
        assertEquals(true, service.list().get("available"));
        Files.delete(game.resolve("df_game_r"));
        assertEquals(false, service.list().get("available"));
        try { service.save(53, settings(true, "INHERIT", "")); fail(); }
        catch (IllegalStateException expected) { }
        Files.write(game.resolve("df_game_r"), new byte[0]);
        assertEquals(true, service.list().get("available"));
        ChannelService disabled = new ChannelService("false", root.toString(), game.toString(), 3, executable.toString(), temp.getRoot().toPath().resolve("monitor_ip.sh").toString(), "cain", fakeProc.toString(), db);
        assertEquals(false, disabled.list().get("available"));
    }

    @Test public void legacySupervisorChannelsCanBeEditedWithoutMigratingOtherChannels() throws Exception {
        Path state = temp.getRoot().toPath().resolve("supervisor-state");
        Files.write(state.resolve("dnf_channel:game_siroco53"), "RUNNING\n".getBytes(StandardCharsets.UTF_8));
        Files.write(state.resolve("dnf_channel:game_siroco54"), "RUNNING\n".getBytes(StandardCharsets.UTF_8));
        Path start = root.resolve("run/start_game.sh");
        Files.write(start, ("#!/bin/bash\ncp /tmp/$channel_name.cfg " + game + "/cfg/$channel_name.cfg\n").getBytes(StandardCharsets.UTF_8));
        assertEquals(2, ((List<?>) service.list().get("channels")).size());
        service.save(53, settings(true, "STATIC", "198.51.100.4"));
        assertEquals(java.util.Collections.singletonList("restart dnf_channel:game_siroco53"), operations());
        assertTrue(Files.readAllLines(start, StandardCharsets.UTF_8).toString().contains("DNF_CHANNEL_MANAGER_IP_OVERRIDE"));
        assertTrue(Files.exists(start.resolveSibling("start_game.sh.before-channel-manager")));
        assertFalse(Files.exists(root.resolve("conf.d/channel-admin-53.conf")));
        clearLog();
        service.save(53, settings(false, "STATIC", "198.51.100.4"));
        assertEquals(java.util.Collections.singletonList("stop dnf_channel:game_siroco53"), operations());
        clearLog();
        Files.write(state.resolve("dnf_channel:game_siroco53"), "RUNNING\n".getBytes(StandardCharsets.UTF_8));
        service.refreshAddresses();
        assertEquals(java.util.Collections.singletonList("stop dnf_channel:game_siroco53"), operations());
        assertEquals("RUNNING", new String(Files.readAllBytes(state.resolve("dnf_channel:game_siroco54")), StandardCharsets.UTF_8).trim());
    }

    private DefaultAddressSettings defaultSettings(String mode, String target) {
        DefaultAddressSettings settings = new DefaultAddressSettings();
        settings.setMode(mode); settings.setTarget(target); return settings;
    }

    @Test public void existingDefaultAddressIsAutomaticallyManagedWithoutRestartingChannels() throws Exception {
        Map<String, Object> overview = service.list();
        Map<?, ?> address = (Map<?, ?>) overview.get("defaultAddress");
        assertEquals(Boolean.TRUE, address.get("managed"));
        assertEquals("STATIC", address.get("mode"));
        assertEquals("192.0.2.1", address.get("target"));
        assertEquals("192.0.2.1", new String(Files.readAllBytes(root.resolve("channel-manager/default-address-ip")), StandardCharsets.UTF_8).trim());
        assertFalse(Files.exists(log));
    }

    @Test public void legacyEnvironmentPreservesDdnsAndExistingPriority() throws Exception {
        java.util.Map<String, String> env = new java.util.HashMap<>();
        env.put("DDNS_ENABLE", "true"); env.put("DDNS_DOMAIN", "example.com");
        assertEquals("DDNS", ChannelService.legacyDefault(env, "192.0.2.1").getMode());
        assertEquals("example.com", ChannelService.legacyDefault(env, "192.0.2.1").getTarget());
        env.put("PUBLIC_IP", "192.0.2.1");
        assertEquals("STATIC", ChannelService.legacyDefault(env, "192.0.2.1").getMode());
        env.remove("PUBLIC_IP"); env.put("DDNS_DOMAIN", "");
        assertEquals("AUTO", ChannelService.legacyDefault(env, "192.0.2.1").getMode());
    }

    @Test public void automaticTakeoverPreservesExistingDdnsConfiguration() throws Exception {
        Path monitor = temp.getRoot().toPath().resolve("monitor_ip.sh");
        Files.write(monitor, "#!/bin/bash\n# DDNS_ENABLE MONITOR_PUBLIC_IP\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(monitor.toFile().setExecutable(true));
        Files.write(supervisorState.resolve("dnf:monitor_ip"), "RUNNING\n".getBytes(StandardCharsets.UTF_8));
        Files.write(supervisorState.resolve("pid-dnf:monitor_ip"), "1234\n".getBytes(StandardCharsets.UTF_8));
        Path process = Files.createDirectories(fakeProc.resolve("1234"));
        Files.write(process.resolve("environ"), "DDNS_ENABLE=true\0DDNS_DOMAIN=localhost\0".getBytes(StandardCharsets.UTF_8));
        Map<?, ?> address = (Map<?, ?>) service.list().get("defaultAddress");
        assertEquals(Boolean.TRUE, address.get("managed"));
        assertEquals("DDNS", address.get("mode"));
        assertEquals("localhost", address.get("target"));
        assertEquals(java.util.Collections.singletonList("restart dnf:monitor_ip"), operations());
        assertEquals("192.0.2.1", new String(Files.readAllBytes(root.resolve("monitor_ip/MONITOR_PUBLIC_IP")), StandardCharsets.UTF_8).trim());
    }

    @Test public void automaticTakeoverDoesNotGuessWhenOriginalMonitorEnvironmentIsUnreadable() throws Exception {
        org.junit.Assume.assumeFalse(System.getenv().containsKey("DDNS_ENABLE"));
        Path monitor = temp.getRoot().toPath().resolve("monitor_ip.sh");
        String original = "#!/bin/bash\n# DDNS_ENABLE MONITOR_PUBLIC_IP\n";
        Files.write(monitor, original.getBytes(StandardCharsets.UTF_8));
        Files.write(supervisorState.resolve("dnf:monitor_ip"), "RUNNING\n".getBytes(StandardCharsets.UTF_8));
        Files.write(supervisorState.resolve("pid-dnf:monitor_ip"), "1234\n".getBytes(StandardCharsets.UTF_8));
        Map<?, ?> address = (Map<?, ?>) service.list().get("defaultAddress");
        assertEquals(Boolean.FALSE, address.get("managed"));
        assertNotNull(address.get("error"));
        assertEquals(original, new String(Files.readAllBytes(monitor), StandardCharsets.UTF_8));
        assertFalse(Files.exists(root.resolve("channel-manager/default-address.properties")));
    }

    @Test public void failedAutomaticTakeoverRestoresExistingCachedAddress() throws Exception {
        Path monitor = temp.getRoot().toPath().resolve("monitor_ip.sh");
        Files.write(monitor, "#!/bin/bash\n# DDNS_ENABLE MONITOR_PUBLIC_IP\n".getBytes(StandardCharsets.UTF_8));
        Files.write(supervisorState.resolve("dnf:monitor_ip"), "RUNNING\n".getBytes(StandardCharsets.UTF_8));
        Files.write(supervisorState.resolve("pid-dnf:monitor_ip"), "1234\n".getBytes(StandardCharsets.UTF_8));
        Path process = Files.createDirectories(fakeProc.resolve("1234"));
        Files.write(process.resolve("environ"), "DDNS_ENABLE=false\0".getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(root.resolve("channel-manager"));
        Files.write(root.resolve("channel-manager/default-address-ip"), "198.51.100.55\n".getBytes(StandardCharsets.UTF_8));
        String script = new String(Files.readAllBytes(executable), StandardCharsets.UTF_8);
        script = script.replace("echo \"$cmd $name\" >> \"$log\"\n", "echo \"$cmd $name\" >> \"$log\"\nif [ \"$cmd\" = restart ] && [ \"$name\" = dnf:monitor_ip ] && [ -e \"$state/fail-once\" ]; then rm \"$state/fail-once\"; exit 1; fi\n");
        Files.write(executable, script.getBytes(StandardCharsets.UTF_8));
        Files.createFile(supervisorState.resolve("fail-once"));
        Map<?, ?> address = (Map<?, ?>) service.list().get("defaultAddress");
        assertEquals(Boolean.FALSE, address.get("managed"));
        assertNotNull(address.get("error"));
        assertEquals("198.51.100.55", new String(Files.readAllBytes(root.resolve("channel-manager/default-address-ip")), StandardCharsets.UTF_8).trim());
        assertFalse(Files.exists(root.resolve("channel-manager/default-address.properties")));
    }

    @Test public void legacyAutomaticPublicIpContinuesRefreshingUnderWebManagement() throws Exception {
        Path discovery = root.resolve("monitor_ip/get_ddns_ip.sh");
        Files.write(discovery, "#!/bin/bash\necho 198.51.100.9\n".getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(root.resolve("channel-manager"));
        Files.write(root.resolve("channel-manager/default-address.properties"), "mode=AUTO\ntarget=\n".getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("channel-manager/default-address-ip"), "192.0.2.1\n".getBytes(StandardCharsets.UTF_8));
        service.save(53, settings(true, "STATIC", "198.51.100.5"));
        clearLog();
        service.refreshAddresses();
        assertEquals("198.51.100.9", new String(Files.readAllBytes(root.resolve("monitor_ip/MONITOR_PUBLIC_IP")), StandardCharsets.UTF_8).trim());
        assertEquals("198.51.100.5", new String(Files.readAllBytes(root.resolve("channel-manager/ips/53")), StandardCharsets.UTF_8).trim());
        assertEquals(java.util.Collections.singletonList("restart channel_admin_53"), operations());
    }

    @Test public void defaultAddressRefreshesSharedServicesAndKeepsChannelIps() throws Exception {
        Path monitor = temp.getRoot().toPath().resolve("monitor_ip.sh");
        Files.write(monitor, "#!/bin/bash\n# DDNS_ENABLE MONITOR_PUBLIC_IP\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(monitor.toFile().setExecutable(true));
        for (String name : new String[]{"dnf:monitor_ip", "dnf:bridge", "dnf_channel:channel"})
            Files.write(supervisorState.resolve(name), "RUNNING\n".getBytes(StandardCharsets.UTF_8));
        service.save(53, settings(true, "INHERIT", ""));
        service.save(54, settings(true, "STATIC", "198.51.100.4"));
        clearLog();
        service.saveDefaultAddress(defaultSettings("STATIC", "198.51.100.8"));
        assertEquals(java.util.Arrays.asList("restart dnf:monitor_ip", "restart dnf:bridge", "restart dnf_channel:channel", "restart channel_admin_53", "restart channel_admin_54"), operations());
        assertEquals("198.51.100.4", new String(Files.readAllBytes(root.resolve("channel-manager/ips/54")), StandardCharsets.UTF_8).trim());
        assertTrue(new String(Files.readAllBytes(monitor), StandardCharsets.UTF_8).contains("DNF_WEB_DEFAULT_ADDRESS_WRAPPER"));
        assertTrue(Files.exists(monitor.resolveSibling("monitor_ip.sh.before-web-default")));
        assertEquals("198.51.100.8", new String(Files.readAllBytes(root.resolve("monitor_ip/MONITOR_PUBLIC_IP")), StandardCharsets.UTF_8).trim());
        Files.delete(root.resolve("channel-manager/default-address-ip"));
        Files.write(supervisorState.resolve("dnf:monitor_ip"), "STOPPED\n".getBytes(StandardCharsets.UTF_8));
        clearLog();
        service.refreshAddresses();
        assertEquals(java.util.Collections.singletonList("start dnf:monitor_ip"), operations());
        assertEquals("198.51.100.8", new String(Files.readAllBytes(root.resolve("channel-manager/default-address-ip")), StandardCharsets.UTF_8).trim());
        service.saveDefaultAddress(defaultSettings("DDNS", "localhost"));
        Files.write(root.resolve("monitor_ip/MONITOR_PUBLIC_IP"), "127.0.0.2\n".getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("channel-manager/default-address-ip"), "127.0.0.2\n".getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("channel-manager/ips/53"), "127.0.0.2\n".getBytes(StandardCharsets.UTF_8));
        clearLog();
        service.refreshAddresses();
        assertEquals(java.util.Arrays.asList("restart dnf:bridge", "restart dnf_channel:channel", "restart channel_admin_53", "restart channel_admin_54"), operations());
        assertEquals("127.0.0.1", new String(Files.readAllBytes(root.resolve("monitor_ip/MONITOR_PUBLIC_IP")), StandardCharsets.UTF_8).trim());
        Files.write(root.resolve("channel-manager/default-address.properties"), "mode=DDNS\ntarget=invalid/domain\n".getBytes(StandardCharsets.UTF_8));
        clearLog();
        service.refreshAddresses();
        assertTrue(operations().isEmpty());
        assertEquals("127.0.0.1", new String(Files.readAllBytes(root.resolve("monitor_ip/MONITOR_PUBLIC_IP")), StandardCharsets.UTF_8).trim());
        assertNotNull(((java.util.Map<?, ?>) service.list().get("defaultAddress")).get("error"));
    }

    @Test public void defaultAddressDoesNotOverwriteAnUnknownMonitorScript() throws Exception {
        Path monitor = temp.getRoot().toPath().resolve("monitor_ip.sh");
        Files.write(monitor, "custom monitor\n".getBytes(StandardCharsets.UTF_8));
        Files.write(supervisorState.resolve("dnf:monitor_ip"), "RUNNING\n".getBytes(StandardCharsets.UTF_8));
        try { service.saveDefaultAddress(defaultSettings("STATIC", "198.51.100.8")); fail(); }
        catch (java.io.IOException expected) { }
        assertEquals("custom monitor\n", new String(Files.readAllBytes(monitor), StandardCharsets.UTF_8));
        assertFalse(Files.exists(root.resolve("channel-manager/default-address.properties")));
    }

    @Test public void defaultAddressAlsoRefreshesExistingLegacyChannelsWithoutSettings() throws Exception {
        Files.write(supervisorState.resolve("dnf_channel:game_siroco55"), "RUNNING\n".getBytes(StandardCharsets.UTF_8));
        clearLog();
        service.saveDefaultAddress(defaultSettings("STATIC", "198.51.100.9"));
        assertEquals(java.util.Collections.singletonList("restart dnf_channel:game_siroco55"), operations());
        assertFalse(Files.exists(root.resolve("channel-manager/channels/55.properties")));
    }

    @Test public void failedDefaultAddressApplyRestoresThePreviousConfiguration() throws Exception {
        Files.write(supervisorState.resolve("dnf:bridge"), "RUNNING\n".getBytes(StandardCharsets.UTF_8));
        String script = new String(Files.readAllBytes(executable), StandardCharsets.UTF_8);
        script = script.replace("echo \"$cmd $name\" >> \"$log\"\n", "echo \"$cmd $name\" >> \"$log\"\nif [ \"$cmd\" = restart ] && [ \"$name\" = dnf:bridge ]; then echo failure; exit 1; fi\n");
        Files.write(executable, script.getBytes(StandardCharsets.UTF_8));
        try { service.saveDefaultAddress(defaultSettings("STATIC", "198.51.100.8")); fail(); }
        catch (IllegalStateException expected) { }
        assertFalse(Files.exists(root.resolve("channel-manager/default-address.properties")));
        assertFalse(Files.exists(root.resolve("channel-manager/default-address-ip")));
        assertEquals("192.0.2.1", new String(Files.readAllBytes(root.resolve("monitor_ip/MONITOR_PUBLIC_IP")), StandardCharsets.UTF_8).trim());
    }

    @Test public void globalChangeKeepsAChannelLastDdnsIpWhenItsLookupFails() throws Exception {
        service.save(53, settings(true, "DDNS", "localhost"));
        Files.write(root.resolve("channel-manager/channels/53.properties"),
                "mode=DDNS\ntarget=invalid/domain\nenabled=true\ntype=\n".getBytes(StandardCharsets.UTF_8));
        clearLog();
        service.saveDefaultAddress(defaultSettings("STATIC", "198.51.100.9"));
        assertEquals(java.util.Collections.singletonList("restart channel_admin_53"), operations());
        assertEquals("127.0.0.1", new String(Files.readAllBytes(root.resolve("channel-manager/ips/53")), StandardCharsets.UTF_8).trim());
        java.util.List<?> channels = (java.util.List<?>) service.list().get("channels");
        assertNotNull(((java.util.Map<?, ?>) channels.get(0)).get("error"));
    }

    @Test public void failedSharedAddressRestartIsRetriedWithoutRestartingOtherChannels() throws Exception {
        service.save(53, settings(true, "INHERIT", ""));
        service.save(54, settings(true, "STATIC", "198.51.100.4"));
        service.refreshAddresses(); // Persist the original shared address.
        String script = new String(Files.readAllBytes(executable), StandardCharsets.UTF_8);
        script = script.replace("echo \"$cmd $name\" >> \"$log\"\n", "echo \"$cmd $name\" >> \"$log\"\nif [ \"$cmd\" = restart ] && [ \"$name\" = channel_admin_54 ] && [ -e \"$state/fail-once\" ]; then rm \"$state/fail-once\"; exit 1; fi\n");
        Files.write(executable, script.getBytes(StandardCharsets.UTF_8));
        Files.createFile(supervisorState.resolve("fail-once"));
        clearLog();
        service.saveDefaultAddress(defaultSettings("STATIC", "198.51.100.8"));
        assertEquals(java.util.Arrays.asList("restart channel_admin_53", "restart channel_admin_54"), operations());
        assertEquals("192.0.2.1", new String(Files.readAllBytes(root.resolve("channel-manager/observed-default-ip")), StandardCharsets.UTF_8).trim());
        clearLog();
        service.refreshAddresses();
        assertEquals(java.util.Collections.singletonList("restart channel_admin_54"), operations());
        assertEquals("198.51.100.8", new String(Files.readAllBytes(root.resolve("channel-manager/observed-default-ip")), StandardCharsets.UTF_8).trim());
    }

    @Test public void changingTypeUpdatesOnlyTheRuntimeGameChannelTable() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        ChannelService channels = new ChannelService("auto", root.toString(), game.toString(), 3,
                executable.toString(), temp.getRoot().toPath().resolve("monitor_ip.sh").toString(), "cain", fakeProc.toString(), source);
        ChannelSettings request = settings(true, "INHERIT", "");
        request.setChannelType(15);
        channels.save(53, request);
        org.mockito.ArgumentCaptor<String> queries = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(connection, atLeastOnce()).prepareStatement(queries.capture());
        assertTrue(queries.getAllValues().stream().anyMatch(sql -> sql.startsWith("UPDATE `taiwan_cain`.`game_channel` SET gc_type")));
        assertTrue(queries.getAllValues().stream().noneMatch(sql -> sql.contains("d_channel")));
    }

    @Test public void returningToGameDefaultReadsConfigurationButWritesOnlyRuntimeType() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement runtimeQuery = mock(PreparedStatement.class);
        PreparedStatement defaultQuery = mock(PreparedStatement.class);
        PreparedStatement update = mock(PreparedStatement.class);
        ResultSet empty = mock(ResultSet.class), defaults = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            return sql.startsWith("UPDATE") ? update : sql.contains("ch_server_data") ? defaultQuery : runtimeQuery;
        });
        when(runtimeQuery.executeQuery()).thenReturn(empty);
        when(defaultQuery.executeQuery()).thenReturn(defaults);
        when(defaults.next()).thenReturn(true);
        when(defaults.getInt(1)).thenReturn(8);
        ChannelService channels = new ChannelService("auto", root.toString(), game.toString(), 3,
                executable.toString(), temp.getRoot().toPath().resolve("monitor_ip.sh").toString(), "cain", fakeProc.toString(), source);
        ChannelSettings chosen = settings(true, "INHERIT", ""); chosen.setChannelType(15);
        channels.save(53, chosen);
        channels.save(53, settings(true, "INHERIT", ""));
        verify(update).setInt(1, 15);
        verify(update).setInt(1, 8);
        org.mockito.ArgumentCaptor<String> queries = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(connection, atLeastOnce()).prepareStatement(queries.capture());
        assertTrue(queries.getAllValues().stream().noneMatch(sql -> sql.startsWith("UPDATE `d_channel`")));
    }

    @Test public void integrationTestModeSuppressesRuntimeTypeWrites() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(mock(ResultSet.class));
        ChannelService channels = new ChannelService("auto", root.toString(), game.toString(), 3,
                executable.toString(), temp.getRoot().toPath().resolve("monitor_ip.sh").toString(), "cain", fakeProc.toString(), source);
        org.springframework.test.util.ReflectionTestUtils.setField(channels, "syncTypes", false);
        ChannelSettings request = settings(true, "INHERIT", ""); request.setChannelType(15);
        channels.save(53, request);
        org.mockito.ArgumentCaptor<String> queries = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(connection, atLeastOnce()).prepareStatement(queries.capture());
        assertTrue(queries.getAllValues().stream().noneMatch(sql -> sql.startsWith("UPDATE")));
    }
}
