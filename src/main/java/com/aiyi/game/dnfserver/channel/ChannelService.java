package com.aiyi.game.dnfserver.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.Security;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Manages a DNF installation visible to this process, on the host or in the same container. */
@Service
public class ChannelService {
    private static final Logger LOG = LoggerFactory.getLogger(ChannelService.class);
    private final boolean disabled;
    private final Path data;
    private final Path gameDir;
    private final Path monitorScript;
    private final int serverGroup;
    private final String supervisor;
    private final Path processRoot;
    private final DataSource dataSource;
    private final String channelTable;
    @Value("${dnf.channels.sync-types:true}")
    private boolean syncTypes = true;
    private final Map<Integer, String> errors = new HashMap<>();
    private final Set<Integer> pendingSharedAddress = new HashSet<>();
    private String defaultAddressError;
    private String observedDefaultIp;

    public ChannelService(@Value("${dnf.channels.enabled:auto}") String enabled,
                          @Value("${dnf.channels.data-dir:/data}") String data,
                          @Value("${dnf.channels.game-dir:/home/neople/game}") String gameDir,
                          @Value("${dnf.channels.server-group:3}") int serverGroup,
                          @Value("${dnf.channels.supervisorctl:/usr/bin/supervisorctl}") String supervisor,
                          @Value("${dnf.channels.monitor-script:/home/template/init/monitor_ip/monitor_ip.sh}") String monitorScript,
                          @Value("${SERVER_GROUP_DB:cain}") String groupDb,
                          @Value("${dnf.channels.process-dir:/proc}") String processDir,
                          DataSource dataSource) {
        this.disabled = "false".equalsIgnoreCase(enabled);
        this.data = Paths.get(data).toAbsolutePath().normalize();
        this.gameDir = Paths.get(gameDir).toAbsolutePath().normalize();
        this.serverGroup = serverGroup;
        this.supervisor = Paths.get(supervisor).toAbsolutePath().normalize().toString();
        this.monitorScript = Paths.get(monitorScript).toAbsolutePath().normalize();
        this.processRoot = Paths.get(processDir).toAbsolutePath().normalize();
        if (serverGroup < 1 || serverGroup > 6) throw new IllegalArgumentException("无效的大区编号");
        if (groupDb.isEmpty()) groupDb = new String[]{"", "cain", "diregie", "siroco", "prey", "casillas", "hilder"}[serverGroup];
        if (!groupDb.matches("[a-zA-Z0-9_]+")) throw new IllegalArgumentException("无效的大区数据库");
        this.channelTable = "`taiwan_" + groupDb + "`.`game_channel`";
        this.dataSource = dataSource;
        try {
            String current = read(defaultIp());
            String previous = read(observedDefault());
            if (isIpv4(previous)) this.observedDefaultIp = previous;
            else if (isIpv4(current)) this.observedDefaultIp = current;
        } catch (IOException e) { LOG.debug("Default address not yet available", e); }
        // The JVM's default positive DNS cache otherwise prevents DDNS from being observed.
        Security.setProperty("networkaddress.cache.ttl", "0");
        Security.setProperty("networkaddress.cache.negative.ttl", "0");
    }

    public synchronized Map<String, Object> list() throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean available = available();
        if (available) ensureDefaultManaged();
        result.put("available", available);
        result.put("serverGroup", serverGroup);
        result.put("dataDir", data.toString());
        result.put("defaultIp", read(defaultIp()));
        result.put("defaultAddress", defaultAddressView());
        result.put("memory", MemoryStats.read());
        List<Map<String, Object>> channels = new ArrayList<>();
        if (available) {
            for (int number : numbers()) channels.add(view(number));
        }
        result.put("channels", channels);
        return result;
    }

    public synchronized Map<String, Object> save(int number, ChannelSettings request) throws Exception {
        requireAvailable();
        validateNumber(number);
        if (request == null || request.getMode() == null) throw new IllegalArgumentException("请选择地址模式");
        String mode = request.getMode().toUpperCase(Locale.ROOT);
        if (!Arrays.asList("INHERIT", "STATIC", "DDNS").contains(mode)) throw new IllegalArgumentException("地址模式不正确");
        String target = request.getTarget() == null ? "" : request.getTarget().trim().toLowerCase(Locale.ROOT);
        if ("INHERIT".equals(mode)) target = "";
        if ("STATIC".equals(mode) && !isIpv4(target)) throw new IllegalArgumentException("请输入有效的 IPv4 地址");
        if ("DDNS".equals(mode) && (!validDomain(target) || isIpv4(target))) throw new IllegalArgumentException("请输入有效的 DDNS 域名");
        Integer channelType = request.getChannelType();
        if (channelType != null && (channelType < -128 || channelType > 127)) throw new IllegalArgumentException("频道类型必须在 -128～127 之间");

        Path settings = settings(number);
        boolean exists = Files.isRegularFile(settings);
        Properties old = exists ? load(settings) : new Properties();
        Integer restoreDefaultType = null;
        if (channelType == null && old.getProperty("type", "").matches("-?[0-9]{1,3}")) {
            try { restoreDefaultType = readGameDefaultType(number); }
            catch (Exception e) { throw new IllegalStateException("无法读取游戏默认频道类型，未更改配置", e); }
            if (restoreDefaultType == null) throw new IllegalStateException("未找到游戏默认频道类型，未更改配置");
        }
        String legacy = legacyPrograms().get(number);
        String status = legacy != null ? run(true, "status", legacy) : exists ? run(true, "status", group(number)) : "";
        boolean registered = status.matches("(?s).*\\b(RUNNING|STOPPED|STARTING|BACKOFF|FATAL|EXITED|STOPPING)\\b.*");
        boolean running = status.matches("(?s).*\\b(RUNNING|STARTING|BACKOFF)\\b.*");
        boolean oldEnabled = exists ? Boolean.parseBoolean(old.getProperty("enabled", "false")) : running;
        String oldIp = read(ipFile(number));
        if (oldIp.isEmpty()) oldIp = read(defaultIp());
        boolean newEnabled = request.isEnabled();
        String resolved;
        try { resolved = resolve(mode, target); }
        catch (IOException e) {
            if (newEnabled) throw new IllegalArgumentException("无法解析 IPv4 地址：" + e.getMessage());
            resolved = oldIp;
        }
        if (newEnabled && resolved.isEmpty()) throw new IllegalArgumentException("无法获得 IPv4 地址，请检查默认地址或 DDNS 解析");
        if (newEnabled) ensureAddressOverride();
        write(settings, "mode=" + mode + "\ntarget=" + target + "\nenabled=" + newEnabled + "\ntype=" + (channelType == null ? "" : channelType) + "\n");
        if (!resolved.isEmpty()) write(ipFile(number), resolved + "\n");
        if (legacy == null) write(programFile(number), program(number, newEnabled));
        try {
            String process = legacy == null ? group(number) : legacy;
            if (!registered) {
                run(false, "reread");
                run(false, "add", group(number));
            } else if (oldEnabled && !newEnabled) {
                if (running) run(false, "stop", process);
            } else if (!oldEnabled && newEnabled) {
                if (!running) run(false, "start", process);
                else if (!resolved.equals(oldIp)) run(false, "restart", process);
            } else if (newEnabled && !resolved.equals(oldIp)) {
                run(false, running ? "restart" : "start", process);
            }
            if (!newEnabled) stopDetachedGames(number);
            if (newEnabled && channelType != null) updateType(number, channelType);
            else if (restoreDefaultType != null) updateType(number, restoreDefaultType);
            errors.remove(number);
        } catch (Exception e) {
            errors.put(number, "配置已保存，但应用失败：" + e.getMessage());
            throw new IllegalStateException(errors.get(number), e);
        }
        return view(number);
    }

    public synchronized Map<String, Object> setEnabled(int number, boolean enabled) throws Exception {
        requireAvailable();
        validateNumber(number);
        Path path = settings(number);
        boolean exists = Files.isRegularFile(path);
        String legacy = legacyPrograms().get(number);
        if (!exists && legacy == null) throw new IllegalArgumentException("频道不存在");
        Properties current = exists ? load(path) : new Properties();
        String status = legacy == null ? "" : run(true, "status", legacy);
        boolean wasEnabled = exists ? Boolean.parseBoolean(current.getProperty("enabled", "false")) :
                status.matches("(?s).*\\b(RUNNING|STARTING|BACKOFF)\\b.*");
        if (enabled == wasEnabled) return view(number);
        ChannelSettings request = new ChannelSettings();
        request.setEnabled(enabled);
        request.setMode(current.getProperty("mode", "INHERIT"));
        request.setTarget(current.getProperty("target", ""));
        String type = current.getProperty("type", "");
        if (!type.isEmpty()) {
            if (!type.matches("-?[0-9]{1,3}")) throw new IllegalStateException("频道类型配置无效，未更改状态");
            request.setChannelType(Integer.valueOf(type));
        }
        return save(number, request);
    }

    public synchronized Map<String, Object> restart(int number) throws Exception {
        requireAvailable();
        validateNumber(number);
        String legacy = legacyPrograms().get(number);
        if (!Files.isRegularFile(settings(number)) && legacy == null) throw new IllegalArgumentException("频道不存在");
        if (Files.isRegularFile(settings(number)) && !Boolean.parseBoolean(load(settings(number)).getProperty("enabled", "false")))
            throw new IllegalArgumentException("频道已停用，请先启用");
        String process = legacy == null ? group(number) : legacy;
        String status = run(true, "status", process);
        run(false, status.matches("(?s).*\\b(RUNNING|STARTING|BACKOFF)\\b.*") ? "restart" : "start", process);
        errors.remove(number);
        return view(number);
    }

    /** PVF changes require the game processes to reload, but not the Web or database. */
    public synchronized boolean canReloadPvf() {
        return available();
    }

    public synchronized Boolean legacyGatewayRegistered() {
        try {
            return Pattern.compile("(?m)^\\s*tongyi_gate(?::tongyi_gate)?\\s+(RUNNING|STARTING|BACKOFF|STOPPED|STOPPING|EXITED|FATAL)\\b")
                    .matcher(run(true, "status")).find();
        } catch (Exception e) {
            LOG.warn("无法读取原网关状态：{}", e.getMessage());
            return null;
        }
    }

    @FunctionalInterface
    public interface PvfInstallation {
        void install() throws Exception;
    }

    public synchronized Map<String, Object> installPvfAndRestart(PvfInstallation installation) throws Exception {
        return installAndRestart(gatewayRegistered -> installation.install(), false);
    }

    @FunctionalInterface
    public interface KeyInstallation {
        void install(boolean gatewayRegistered) throws Exception;
    }

    public synchronized Map<String, Object> installKeyAndRestart(KeyInstallation installation) throws Exception {
        return installAndRestart(installation, true);
    }

    private Map<String, Object> installAndRestart(KeyInstallation installation, boolean includeGateway) throws Exception {
        requireAvailable();
        String status = run(true, "status");
        Pattern game = Pattern.compile("(?m)^\\s*(dnf_channel:game_" + Pattern.quote(groupName()) +
                "[0-9]{2}|channel_admin_[0-9]{2}(?::channel_admin_[0-9]{2})?)\\s+(RUNNING|STARTING|BACKOFF)\\b");
        Matcher matcher = game.matcher(status);
        List<String> running = new ArrayList<>();
        while (matcher.find()) running.add(matcher.group(1));
        Matcher anyGame = Pattern.compile("(?m)^\\s*(dnf_channel:game_\\S+|channel_admin_\\S+)\\s+(RUNNING|STARTING|BACKOFF)\\b").matcher(status);
        while (anyGame.find()) {
            if (!running.contains(anyGame.group(1))) {
                throw new IllegalStateException("发现无法识别的游戏进程，无法安全重启：" + anyGame.group(1));
            }
        }

        Matcher gateway = Pattern.compile("(?m)^\\s*(tongyi_gate(?::tongyi_gate)?)\\s+(RUNNING|STARTING|BACKOFF|STOPPED|STOPPING|EXITED|FATAL)\\b").matcher(status);
        boolean gatewayRegistered = includeGateway && gateway.find();
        if (gatewayRegistered && gateway.group(2).matches("RUNNING|STARTING|BACKOFF")) running.add(gateway.group(1));

        // Do not change any files until Supervisor has been checked and a restart plan exists.
        installation.install(gatewayRegistered);
        List<String> restarted = new ArrayList<>();
        List<Map<String, String>> failed = new ArrayList<>();
        for (String process : running) {
            try {
                run(false, "restart", process);
                restarted.add(process);
            } catch (Exception e) {
                Map<String, String> failure = new LinkedHashMap<>();
                failure.put("process", process);
                failure.put("message", e.getMessage() == null ? "重启失败" : e.getMessage());
                failed.add(failure);
                LOG.warn("配置已更新，但重启进程 {} 失败", process, e);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("restarted", restarted);
        result.put("failed", failed);
        result.put("runningCount", running.size());
        return result;
    }

    public synchronized Map<String, Object> saveDefaultAddress(DefaultAddressSettings request) throws Exception {
        requireAvailable();
        if (request == null || request.getMode() == null) throw new IllegalArgumentException("请选择默认地址模式");
        String mode = request.getMode().toUpperCase(Locale.ROOT);
        String target = request.getTarget() == null ? "" : request.getTarget().trim().toLowerCase(Locale.ROOT);
        if ("STATIC".equals(mode) && !isIpv4(target)) throw new IllegalArgumentException("请输入有效的 IPv4 地址");
        if ("DDNS".equals(mode) && (!validDomain(target) || isIpv4(target))) throw new IllegalArgumentException("请输入有效的 DDNS 域名");
        if (!"STATIC".equals(mode) && !"DDNS".equals(mode)) throw new IllegalArgumentException("默认地址模式不正确");
        String resolved;
        try { resolved = resolve(mode, target); }
        catch (IOException e) { throw new IllegalArgumentException("无法解析 IPv4 地址：" + e.getMessage()); }
        if (resolved.isEmpty()) throw new IllegalArgumentException("无法获得 IPv4 地址");

        Path config = defaultSettings();
        Path last = defaultLastIp();
        boolean hadConfig = Files.isRegularFile(config), hadLast = Files.isRegularFile(last), hadIp = Files.isRegularFile(defaultIp());
        String oldConfig = hadConfig ? new String(Files.readAllBytes(config), StandardCharsets.UTF_8) : "";
        String oldLast = read(last), oldIp = read(defaultIp());
        int monitorState = installDefaultMonitor();
        boolean monitor = monitorState > 0;
        try {
            write(last, resolved + "\n");
            write(config, "mode=" + mode + "\ntarget=" + target + "\n");
            if (monitor) activateDefaultMonitor(!hadConfig || monitorState == 2);
            applyDefaultAddress(oldIp, resolved);
            defaultAddressError = null;
            refreshAddresses(!oldIp.equals(resolved));
            return defaultAddressView();
        } catch (Exception e) {
            if (hadConfig) write(config, oldConfig); else Files.deleteIfExists(config);
            if (hadLast) write(last, oldLast + "\n"); else Files.deleteIfExists(last);
            if (hadIp) write(defaultIp(), oldIp + "\n"); else Files.deleteIfExists(defaultIp());
            if (monitor) {
                try { activateDefaultMonitor(true); }
                catch (Exception rollbackError) { LOG.warn("Could not restore the original IP monitor", rollbackError); }
            }
            throw new IllegalStateException("默认地址未能应用：" + e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelayString = "${dnf.channels.interval-ms:10000}", initialDelay = 15000)
    public synchronized void refreshAddresses() {
        refreshAddresses(false);
    }

    private void refreshAddresses(boolean defaultChangedBySave) {
        if (!available()) return;
        try {
            ensureDefaultManaged();
            boolean defaultChanged = refreshDefaultAddress() || defaultChangedBySave;
            String currentDefault = read(defaultIp());
            if (isIpv4(currentDefault)) {
                if (observedDefaultIp != null && !observedDefaultIp.equals(currentDefault)) defaultChanged = true;
                observedDefaultIp = currentDefault;
            }
            Map<Integer, String> legacyPrograms = legacyPrograms();
            List<Integer> currentChannels = numbers();
            pendingSharedAddress.retainAll(currentChannels);
            for (int number : currentChannels) {
                if (defaultChanged) pendingSharedAddress.add(number);
                try {
                    String legacy = legacyPrograms.get(number);
                    if (!Files.isRegularFile(settings(number))) {
                        if (pendingSharedAddress.contains(number) && legacy != null) restartIfRunning(legacy);
                        pendingSharedAddress.remove(number);
                        continue;
                    }
                    Properties p = load(settings(number));
                    if (!Boolean.parseBoolean(p.getProperty("enabled", "false"))) {
                        if (legacy != null && run(true, "status", legacy).matches("(?s).*\\b(RUNNING|STARTING|BACKOFF)\\b.*")) {
                            run(false, "stop", legacy);
                            stopDetachedGames(number);
                        }
                        pendingSharedAddress.remove(number);
                        continue;
                    }
                    String previous = read(ipFile(number));
                    String next;
                    String resolutionError = null;
                    try {
                        next = resolve(p.getProperty("mode", "INHERIT"), p.getProperty("target", ""));
                        if (next.isEmpty()) throw new IOException("无法解析 IPv4");
                    } catch (IOException e) {
                        if (!pendingSharedAddress.contains(number) || !isIpv4(previous)) throw e;
                        next = previous;
                        resolutionError = "地址解析失败，保留上次地址：" + e.getMessage();
                    }
                    boolean addressChanged = !previous.equals(next);
                    if (addressChanged) write(ipFile(number), next + "\n");
                    // A global change also changes relay/STUN addresses in every
                    // game config, even for channels with an independent IP.
                    if (pendingSharedAddress.contains(number) || (!previous.isEmpty() && addressChanged)) {
                        try {
                            String process = legacy == null ? group(number) : legacy;
                            String status = run(true, "status", process);
                            boolean running = status.matches("(?s).*\\b(RUNNING|STARTING|BACKOFF)\\b.*");
                            run(false, running ? "restart" : "start", process);
                        } catch (Exception e) {
                            if (!previous.isEmpty()) write(ipFile(number), previous + "\n");
                            throw e;
                        }
                    }
                    pendingSharedAddress.remove(number);
                    String type = p.getProperty("type", "");
                    if (Boolean.parseBoolean(p.getProperty("enabled", "false")) && type.matches("-?[0-9]{1,3}")) updateType(number, Integer.parseInt(type));
                    if (resolutionError != null) errors.put(number, resolutionError);
                    else if (!String.valueOf(errors.get(number)).startsWith("配置已保存")) errors.remove(number);
                } catch (Exception e) {
                    errors.put(number, e.getMessage());
                    LOG.warn("Channel {} address refresh failed: {}", number, e.getMessage());
                }
            }
            if (pendingSharedAddress.isEmpty() && isIpv4(currentDefault) && !currentDefault.equals(read(observedDefault())))
                write(observedDefault(), currentDefault + "\n");
        } catch (Exception e) {
            LOG.warn("Could not inspect channels: {}", e.getMessage());
        }
    }

    private Map<String, Object> defaultAddressView() throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean managed = Files.isRegularFile(defaultSettings());
        Properties settings = managed ? load(defaultSettings()) : new Properties();
        result.put("managed", managed);
        result.put("mode", settings.getProperty("mode", "UNMANAGED"));
        result.put("target", settings.getProperty("target", ""));
        result.put("resolvedIp", read(defaultIp()));
        result.put("error", defaultAddressError);
        return result;
    }

    private void ensureDefaultManaged() {
        if (Files.isRegularFile(defaultSettings())) return;
        boolean wroteConfig = false, wroteLast = false, hadLast = false;
        String oldLast = "";
        int monitor = 0;
        try {
            String current = read(defaultIp());
            if (!isIpv4(current)) return;
            Map<String, String> environment = legacyMonitorEnvironment();
            DefaultAddressSettings initial = legacyDefault(environment, current);
            if ("AUTO".equals(initial.getMode()) && !Files.isRegularFile(data.resolve("monitor_ip/get_ddns_ip.sh")))
                throw new IOException("原自动公网 IP 脚本不存在，请手动选择默认地址");
            monitor = installDefaultMonitor();
            hadLast = Files.isRegularFile(defaultLastIp());
            oldLast = hadLast ? read(defaultLastIp()) : "";
            write(defaultLastIp(), current + "\n");
            wroteLast = true;
            write(defaultSettings(), "mode=" + initial.getMode() + "\ntarget=" + initial.getTarget() + "\n");
            wroteConfig = true;
            if (monitor > 0) activateDefaultMonitor(true);
            defaultAddressError = null;
        } catch (Exception e) {
            try {
                if (wroteConfig) Files.deleteIfExists(defaultSettings());
                if (wroteLast) { if (hadLast) write(defaultLastIp(), oldLast + "\n"); else Files.deleteIfExists(defaultLastIp()); }
            } catch (IOException cleanup) { LOG.warn("Could not restore incomplete default address settings", cleanup); }
            if (monitor > 0) {
                try { activateDefaultMonitor(true); }
                catch (Exception rollback) { LOG.warn("Could not restore the original IP monitor", rollback); }
            }
            defaultAddressError = "无法自动接管默认地址：" + e.getMessage();
            LOG.warn("Could not take over the DNF default address: {}", e.getMessage());
        }
    }

    static DefaultAddressSettings legacyDefault(Map<String, String> env, String current) throws IOException {
        DefaultAddressSettings settings = new DefaultAddressSettings();
        boolean fixed = !env.getOrDefault("PUBLIC_IP", "").trim().isEmpty() ||
                "true".equalsIgnoreCase(env.getOrDefault("AUTO_PUBLIC_IP", "")) ||
                (!env.getOrDefault("NB_SETUP_KEY", "").isEmpty() && !env.getOrDefault("NB_MANAGEMENT_URL", "").isEmpty()) ||
                (!env.getOrDefault("TS_AUTH_KEY", "").isEmpty() && !env.getOrDefault("TS_LOGIN_SERVER", "").isEmpty());
        if (!fixed && "true".equalsIgnoreCase(env.getOrDefault("DDNS_ENABLE", ""))) {
            String domain = env.getOrDefault("DDNS_DOMAIN", "").trim().toLowerCase(Locale.ROOT);
            if (domain.isEmpty()) { settings.setMode("AUTO"); settings.setTarget(""); }
            else if (validDomain(domain) && !isIpv4(domain)) { settings.setMode("DDNS"); settings.setTarget(domain); }
            else throw new IOException("原 DDNS 域名无效，请手动选择默认地址");
        } else { settings.setMode("STATIC"); settings.setTarget(current); }
        return settings;
    }

    private Map<String, String> legacyMonitorEnvironment() throws Exception {
        List<String> keys = Arrays.asList("PUBLIC_IP", "AUTO_PUBLIC_IP", "DDNS_ENABLE", "DDNS_DOMAIN",
                "NB_SETUP_KEY", "NB_MANAGEMENT_URL", "TS_AUTH_KEY", "TS_LOGIN_SERVER");
        Map<String, String> environment = new HashMap<>();
        for (String key : keys) if (System.getenv(key) != null) environment.put(key, System.getenv(key));
        String status = run(true, "status", "dnf:monitor_ip");
        boolean registered = status.matches("(?s).*\\b(RUNNING|STOPPED|STARTING|BACKOFF|FATAL|EXITED|STOPPING)\\b.*");
        if (!registered) return environment;
        boolean running = status.matches("(?s).*\\b(RUNNING|STARTING|BACKOFF)\\b.*");
        String rawPid = running ? run(true, "pid", "dnf:monitor_ip").trim() : run(true, "pid").trim();
        if (rawPid.matches("[1-9][0-9]*")) {
            Path file = processRoot.resolve(rawPid).resolve("environ");
            if (Files.isReadable(file)) {
                environment.clear();
                for (String entry : new String(Files.readAllBytes(file), StandardCharsets.UTF_8).split("\\x00")) {
                    int equals = entry.indexOf('=');
                    if (equals > 0 && keys.contains(entry.substring(0, equals))) environment.put(entry.substring(0, equals), entry.substring(equals + 1));
                }
                return environment;
            }
        }
        if (!environment.containsKey("DDNS_ENABLE")) throw new IOException("无法读取原地址监视进程的配置，请手动选择固定 IP 或 DDNS");
        return environment;
    }

    private boolean refreshDefaultAddress() {
        if (!Files.isRegularFile(defaultSettings())) return false;
        try {
            Properties settings = load(defaultSettings());
            String next = resolveDefault(settings.getProperty("mode", ""), settings.getProperty("target", ""));
            String old = read(defaultIp());
            if (next.isEmpty()) throw new IOException("无法解析 IPv4 地址");
            int monitor = installDefaultMonitor();
            if (monitor > 0 && !isIpv4(read(defaultLastIp())))
                write(defaultLastIp(), (isIpv4(old) ? old : next) + "\n");
            if (monitor > 0) activateDefaultMonitor(monitor == 2);
            if (!next.equals(old) || !next.equals(read(defaultIp()))) {
                write(defaultLastIp(), next + "\n");
                try { applyDefaultAddress(old, next); }
                catch (Exception e) {
                    if (!old.isEmpty()) { write(defaultLastIp(), old + "\n"); write(defaultIp(), old + "\n"); }
                    throw e;
                }
            } else if (!next.equals(read(defaultLastIp()))) write(defaultLastIp(), next + "\n");
            defaultAddressError = null;
            return !next.equals(old);
        } catch (Exception e) {
            defaultAddressError = "默认地址更新失败，保留上次地址：" + e.getMessage();
            LOG.warn("Default address refresh failed: {}", e.getMessage());
            return false;
        }
    }

    private String resolveDefault(String mode, String target) throws IOException {
        if (!"AUTO".equals(mode)) return resolve(mode, target);
        Path script = data.resolve("monitor_ip/get_ddns_ip.sh");
        if (!Files.isRegularFile(script)) throw new IOException("自动公网 IP 脚本不存在");
        Process process = new ProcessBuilder("/bin/bash", script.toString()).redirectErrorStream(true).start();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IOException("自动公网 IP 查询超时"); }
            String address = new String(readAll(process.getInputStream()), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0 || !isIpv4(address)) throw new IOException("未获得有效的公网 IPv4 地址");
            return address;
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
    }

    private void applyDefaultAddress(String previous, String next) throws Exception {
        if (next.equals(previous) && next.equals(read(defaultIp()))) return;
        write(defaultIp(), next + "\n");
        if (next.equals(previous)) return;
        restartIfRunning("dnf:bridge");
        restartIfRunning("dnf_channel:channel");
    }

    private void restartIfRunning(String name) throws Exception {
        String status = run(true, "status", name);
        if (status.matches("(?s).*\\b(RUNNING|STARTING|BACKOFF)\\b.*")) run(false, "restart", name);
    }

    // 0: no original monitor, 1: already wrapped, 2: wrapper installed now.
    private int installDefaultMonitor() throws Exception {
        String status = run(true, "status", "dnf:monitor_ip");
        boolean registered = status.matches("(?s).*\\b(RUNNING|STOPPED|STARTING|BACKOFF|FATAL|EXITED|STOPPING)\\b.*");
        if (!registered) {
            if (Files.isRegularFile(monitorScript)) throw new IOException("检测到 DNF 地址监视脚本，但无法确认 Supervisor 状态");
            return 0;
        }
        if (!Files.isRegularFile(monitorScript)) throw new IOException("未找到 DNF 地址监视脚本：" + monitorScript);
        String original = new String(Files.readAllBytes(monitorScript), StandardCharsets.UTF_8);
        Path backup = monitorScript.resolveSibling(monitorScript.getFileName() + ".before-web-default");
        if (original.contains("# DNF_WEB_DEFAULT_ADDRESS_WRAPPER")) {
            if (!Files.isRegularFile(backup)) throw new IOException("地址监视脚本缺少原始备份，未更改配置");
            return 1;
        }
        if (!original.contains("MONITOR_PUBLIC_IP") || !original.contains("DDNS_ENABLE"))
            throw new IOException("无法识别 DNF 地址监视脚本，未修改原配置");
        if (!Files.exists(backup)) Files.copy(monitorScript, backup, StandardCopyOption.COPY_ATTRIBUTES);
        else if (!original.equals(new String(Files.readAllBytes(backup), StandardCharsets.UTF_8)))
            throw new IOException("地址监视脚本与已有备份不一致，未修改原文件");
        Set<java.nio.file.attribute.PosixFilePermission> permissions = Files.getPosixFilePermissions(monitorScript);
        write(monitorScript, defaultMonitorWrapper(data, backup));
        Files.setPosixFilePermissions(monitorScript, permissions);
        return 2;
    }

    private void activateDefaultMonitor(boolean forceRestart) throws Exception {
        String status = run(true, "status", "dnf:monitor_ip");
        boolean running = status.matches("(?s).*\\b(RUNNING|STARTING|BACKOFF)\\b.*");
        if (forceRestart || !running) run(false, running ? "restart" : "start", "dnf:monitor_ip");
    }

    static String defaultMonitorWrapper(Path data, Path backup) {
        String config = shellQuote(data.resolve("channel-manager/default-address.properties"));
        String last = shellQuote(data.resolve("channel-manager/default-address-ip"));
        String ip = shellQuote(data.resolve("monitor_ip/MONITOR_PUBLIC_IP"));
        return "#!/bin/bash\n# DNF_WEB_DEFAULT_ADDRESS_WRAPPER\nset -e\n" +
                "if [ -f " + config + " ]; then\n" +
                "  address=$(cat " + last + " 2>/dev/null || true)\n" +
                "  if [[ ! \"$address\" =~ ^[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+$ ]]; then echo 'Managed default address unavailable' >&2; exit 1; fi\n" +
                "  printf '%s\\n' \"$address\" > " + ip + ".tmp\n  mv -f " + ip + ".tmp " + ip + "\n" +
                "  while [ -f " + config + " ]; do sleep 5; done\nfi\nexec " + shellQuote(backup) + " \"$@\"\n";
    }

    private static String shellQuote(Path path) { return "'" + path.toString().replace("'", "'\\''") + "'"; }

    private Map<String, Object> view(int number) throws IOException {
        Properties p = Files.isRegularFile(settings(number)) ? load(settings(number)) : new Properties();
        String process = legacyPrograms().getOrDefault(number, group(number));
        String status;
        try { status = run(true, "status", process).trim(); }
        catch (Exception e) { status = "UNKNOWN: " + e.getMessage(); }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("number", number);
        out.put("enabled", Boolean.parseBoolean(p.getProperty("enabled", Boolean.toString(status.matches("(?s).*\\b(RUNNING|STARTING|BACKOFF)\\b.*")))));
        out.put("mode", p.getProperty("mode", "INHERIT"));
        out.put("target", p.getProperty("target", ""));
        String type = p.getProperty("type", "");
        out.put("channelType", type.matches("-?[0-9]{1,3}") ? Integer.valueOf(type) : null);
        try { out.put("actualType", readType(number)); } catch (Exception e) { out.put("actualType", null); }
        String current = read(ipFile(number));
        out.put("resolvedIp", current.isEmpty() ? read(defaultIp()) : current);
        out.put("tcpPort", serverGroup * 10000 + number);
        out.put("udpPort", serverGroup * 10000 + 1000 + number);
        out.put("listening", isListening(serverGroup * 10000 + number));
        out.put("memoryBytes", channelMemory(number));
        out.put("error", errors.get(number));
        out.put("status", status);
        return out;
    }

    private static Boolean isListening(int port) {
        boolean checked = false;
        for (String file : Arrays.asList("/proc/net/tcp", "/proc/net/tcp6")) {
            Path path = Paths.get(file);
            if (!Files.isReadable(path)) continue;
            try (Stream<String> lines = Files.lines(path, StandardCharsets.US_ASCII)) {
                boolean found = lines.anyMatch(line -> isListeningLine(line, port));
                checked = true;
                if (found) return true;
            } catch (IOException | UncheckedIOException e) {
                LOG.warn("Unable to inspect channel sockets: {}", e.getMessage());
            }
        }
        if (checked) return Boolean.FALSE;
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
                return Boolean.TRUE;
            } catch (java.net.ConnectException e) { return Boolean.FALSE; }
            catch (IOException e) { return null; }
        }
        return null;
    }

    static boolean isListeningLine(String line, int port) {
        String[] fields = line.trim().split("\\s+");
        if (fields.length < 4 || !"0A".equalsIgnoreCase(fields[3])) return false;
        int separator = fields[1].lastIndexOf(':');
        if (separator < 0) return false;
        try { return Integer.parseInt(fields[1].substring(separator + 1), 16) == port; }
        catch (NumberFormatException e) { return false; }
    }

    /** DNF's df_game_r daemon leaves the Supervisor process group after starting. */
    private void stopDetachedGames(int number) throws Exception {
        List<Integer> pids = gamePids(number);
        for (int pid : pids) signalGame(pid, number, "-TERM");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline && anyGamePidMatches(pids, number)) Thread.sleep(200);
        for (int pid : pids) if (isGamePid(pid, number)) signalGame(pid, number, "-KILL");
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline && anyGamePidMatches(pids, number)) Thread.sleep(100);
        if (!gamePids(number).isEmpty()) throw new IOException("频道游戏进程未能停止，请检查进程状态");
    }

    private boolean anyGamePidMatches(List<Integer> pids, int number) {
        for (int pid : pids) if (isGamePid(pid, number)) return true;
        return false;
    }

    private List<Integer> gamePids(int number) throws IOException {
        if (!Files.isDirectory(processRoot) && System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac"))
            return macGamePids(number);
        try (Stream<Path> paths = Files.list(processRoot)) {
            return paths.map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("[0-9]+"))
                    .map(Integer::parseInt)
                    .filter(pid -> isGamePid(pid, number))
                    .collect(Collectors.toList());
        }
    }

    private Long channelMemory(int number) {
        try {
            List<Integer> pids = gamePids(number);
            long total = 0;
            for (int pid : pids) {
                if (Files.isDirectory(processRoot)) {
                    for (String line : Files.readAllLines(processRoot.resolve(Integer.toString(pid)).resolve("status"), StandardCharsets.US_ASCII)) {
                        if (line.startsWith("VmRSS:")) {
                            String[] fields = line.trim().split("\\s+");
                            total += Long.parseLong(fields[1]) * 1024L;
                            break;
                        }
                    }
                } else {
                    String rss = ps("-p", Integer.toString(pid), "-o", "rss=").trim();
                    if (!rss.isEmpty()) total += Long.parseLong(rss) * 1024L;
                }
            }
            return total;
        } catch (Exception e) { return null; }
    }

    private boolean isGamePid(int pid, int number) {
        if (!Files.isDirectory(processRoot) && System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            try { return isMacGamePid(pid, number); }
            catch (IOException e) { return false; }
        }
        try {
            byte[] cmdline = Files.readAllBytes(processRoot.resolve(Integer.toString(pid)).resolve("cmdline"));
            return isGameCommand(cmdline, groupName(), id(number));
        } catch (IOException | SecurityException e) { return false; }
    }

    private List<Integer> macGamePids(int number) throws IOException {
        List<Integer> result = new ArrayList<>();
        String output = ps("-axo", "pid=,command=");
        for (String line : output.split("\n")) {
            Matcher match = Pattern.compile("^\\s*([0-9]+)\\s+(.+)$").matcher(line);
            if (!match.matches() || !macGameCommand(match.group(2), number)) continue;
            int pid = Integer.parseInt(match.group(1));
            if (isMacGamePid(pid, number)) result.add(pid);
        }
        return result;
    }

    private boolean isMacGamePid(int pid, int number) throws IOException {
        String command = ps("-p", Integer.toString(pid), "-o", "command=").trim();
        String executable = ps("-p", Integer.toString(pid), "-o", "comm=").trim();
        int slash = executable.lastIndexOf('/');
        return "df_game_r".equals(executable.substring(slash + 1)) && macGameCommand(command, number);
    }

    private boolean macGameCommand(String command, int number) {
        String suffix = " " + groupName() + id(number) + " start";
        if (!command.endsWith(suffix)) return false;
        String executable = command.substring(0, command.length() - suffix.length());
        int slash = executable.lastIndexOf('/');
        return "df_game_r".equals(executable.substring(slash + 1));
    }

    private static String ps(String... arguments) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("/bin/ps");
        Collections.addAll(command, arguments);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        byte[] output = readAll(process.getInputStream());
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IOException("进程检查超时"); }
            return process.exitValue() == 0 ? new String(output, StandardCharsets.UTF_8) : "";
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
    }

    static boolean isGameCommand(byte[] cmdline, String group, String channel) {
        String[] args = new String(cmdline, StandardCharsets.UTF_8).split("\\x00");
        if (args.length != 3 || args[0].isEmpty()) return false;
        String executable = args[0].substring(args[0].lastIndexOf('/') + 1);
        return "df_game_r".equals(executable) && (group + channel).equals(args[1]) && "start".equals(args[2]);
    }

    private String groupName() {
        return new String[]{"", "cain", "diregie", "siroco", "prey", "casillas", "hilder"}[serverGroup];
    }

    private void signalGame(int pid, int number, String signal) throws Exception {
        // Check again immediately before signalling, so stale PID files or unrelated
        // processes are never treated as this channel's game process.
        if (!isGamePid(pid, number)) return;
        Process process = new ProcessBuilder("/bin/kill", signal, Integer.toString(pid)).redirectErrorStream(true).start();
        if (!process.waitFor(5, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IOException("停止频道进程超时"); }
        if (process.exitValue() != 0 && isGamePid(pid, number)) throw new IOException("无法停止频道游戏进程");
    }

    private String resolve(String mode, String target) throws IOException {
        if ("INHERIT".equals(mode)) {
            String ip = read(defaultIp());
            if (ip.isEmpty()) return "";
            if (!isIpv4(ip)) throw new IOException("默认通告地址不是有效的 IPv4");
            return ip;
        }
        if ("STATIC".equals(mode)) {
            if (!isIpv4(target)) throw new IOException("静态地址不是有效的 IPv4");
            return target;
        }
        if (!"DDNS".equals(mode) || !validDomain(target)) throw new IOException("无效的 DDNS 域名");
        for (InetAddress address : InetAddress.getAllByName(target)) if (address instanceof Inet4Address) return address.getHostAddress();
        throw new IOException("域名没有 IPv4 记录");
    }

    private Integer readType(int number) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT gc_type FROM " + channelTable + " WHERE gc_no = ?")) {
            statement.setInt(1, serverGroup * 1000 + number);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getInt(1) : null; }
        }
    }

    private Integer readGameDefaultType(int number) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT channel_kind FROM `d_channel`.`ch_server_data` WHERE server_id = ? AND channel_number = ? LIMIT 1")) {
            statement.setInt(1, serverGroup);
            statement.setInt(2, number);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getInt(1) : null; }
        }
    }

    private void updateType(int number, int type) throws Exception {
        if (!syncTypes) return;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE " + channelTable + " SET gc_type = ? WHERE gc_no = ? AND gc_type <> ?")) {
            statement.setInt(1, type);
            statement.setInt(2, serverGroup * 1000 + number);
            statement.setInt(3, type);
            statement.executeUpdate();
        }
    }

    private static boolean validDomain(String value) {
        return value.length() <= 253 && value.matches("(?i)^(?=.{1,253}$)[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*\\.?$");
    }
    private static boolean isIpv4(String value) {
        if (!value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return false;
        for (String part : value.split("\\.")) if (Integer.parseInt(part) > 255 || (part.length() > 1 && part.startsWith("0"))) return false;
        return true;
    }
    private boolean available() {
        if (disabled || !Files.isRegularFile(data.resolve("run/start_game.sh")) ||
                !Files.isRegularFile(gameDir.resolve("df_game_r")) || !Files.isExecutable(Paths.get(supervisor))) return false;
        try {
            String status = run(true, "status");
            return status.contains("dnf_channel:channel") || status.contains("channel_admin_") ||
                    status.contains("dnf_channel:game_" + groupName());
        } catch (Exception e) { return false; }
    }

    private void requireAvailable() {
        if (!available()) throw new IllegalStateException("未发现可管理的本地 DNF 游戏服务");
    }
    private static void validateNumber(int n) {
        if (!(n == 1 || n == 6 || n == 7 || (n >= 11 && n <= 39) || (n >= 52 && n <= 56)))
            throw new IllegalArgumentException("此版本支持频道 1、6、7、11～39、52～56");
    }
    private String id(int n) { return String.format(Locale.ROOT, "%02d", n); }
    private String group(int n) { return "channel_admin_" + id(n); }
    private Path settings(int n) { return data.resolve("channel-manager/channels/" + id(n) + ".properties"); }
    private Path ipFile(int n) { return data.resolve("channel-manager/ips/" + id(n)); }
    private Path defaultIp() { return data.resolve("monitor_ip/MONITOR_PUBLIC_IP"); }
    private Path defaultSettings() { return data.resolve("channel-manager/default-address.properties"); }
    private Path defaultLastIp() { return data.resolve("channel-manager/default-address-ip"); }
    private Path observedDefault() { return data.resolve("channel-manager/observed-default-ip"); }
    private Path programFile(int n) { return data.resolve("conf.d/channel-admin-" + id(n) + ".conf"); }
    private List<Integer> numbers() throws IOException {
        Path dir = data.resolve("channel-manager/channels");
        Set<Integer> result = new TreeSet<>(legacyPrograms().keySet());
        if (!Files.isDirectory(dir)) return new ArrayList<>(result);
        try (Stream<Path> files = Files.list(dir)) {
            files.map(p -> p.getFileName().toString()).filter(s -> s.matches("[0-9]{2}\\.properties"))
                    .map(s -> Integer.parseInt(s.substring(0, 2))).forEach(result::add);
        }
        return new ArrayList<>(result);
    }

    private Map<Integer, String> legacyPrograms() {
        Map<Integer, String> programs = new TreeMap<>();
        try {
            Matcher matcher = Pattern.compile("(?m)^\\s*(dnf_channel:game_" + Pattern.quote(groupName()) + "([0-9]{2}))\\s+\\S+").matcher(run(true, "status"));
            while (matcher.find()) programs.put(Integer.parseInt(matcher.group(2)), matcher.group(1));
        } catch (Exception e) { LOG.debug("Could not inspect existing DNF channel programs", e); }
        return programs;
    }
    private static Properties load(Path path) throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(path)) { p.load(in); }
        return p;
    }
    private static String read(Path path) throws IOException {
        return Files.isRegularFile(path) ? new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim() : "";
    }
    private static void write(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), ".channel-", ".tmp");
        try {
            Files.write(temp, text.getBytes(StandardCharsets.UTF_8));
            try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }

    private void ensureAddressOverride() throws IOException {
        Path script = data.resolve("run/start_game.sh");
        String source = new String(Files.readAllBytes(script), StandardCharsets.UTF_8);
        if (source.contains("# DNF_CHANNEL_MANAGER_IP_OVERRIDE")) return;
        String anchor = "cp /tmp/$channel_name.cfg " + gameDir + "/cfg/$channel_name.cfg";
        int position = source.indexOf(anchor);
        if (position < 0 || source.indexOf(anchor, position + anchor.length()) >= 0)
            throw new IOException("无法识别游戏启动脚本，未修改频道配置");
        Path backup = script.resolveSibling("start_game.sh.before-channel-manager");
        if (!Files.exists(backup)) Files.copy(script, backup, StandardCopyOption.COPY_ATTRIBUTES);
        Set<java.nio.file.attribute.PosixFilePermission> permissions = Files.getPosixFilePermissions(script);
        String ipDir = "'" + data.resolve("channel-manager/ips").toString().replace("'", "'\\''") + "'";
        String injection = "# DNF_CHANNEL_MANAGER_IP_OVERRIDE\n" +
                "channel_override=$(cat " + ipDir + "/\"$channel_no\" 2>/dev/null || true)\n" +
                "if [[ \"$channel_override\" =~ ^[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+$ ]]; then\n" +
                "  sed -i \"s/^ip[[:space:]]*=.*/ip = $channel_override/\" /tmp/$channel_name.cfg\nfi\n";
        write(script, source.substring(0, position) + injection + source.substring(position));
        Files.setPosixFilePermissions(script, permissions);
    }

    private static String supervisorPath(Path path) {
        String value = path.toString();
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) throw new IllegalArgumentException("DNF 路径不能包含换行符");
        return value.replace("%", "%%");
    }

    private String program(int n, boolean active) {
        int sequence = n >= 11 && n <= 39 ? 3 : 5;
        String name = group(n);
        String start = supervisorPath(data.resolve("run/start_game.sh")).replace("\\", "\\\\").replace("\"", "\\\"");
        return "[program:" + name + "]\ncommand=/bin/bash \"" + start + "\" " + id(n) + " " + sequence + "\n"
                + "directory=" + supervisorPath(gameDir) + "\nautostart=" + active + "\nautorestart=true\nstopasgroup=true\nkillasgroup=true\n"
                + "stdout_logfile=" + supervisorPath(data.resolve("log/" + name + ".log")) + "\nredirect_stderr=true\nstdout_logfile_maxbytes=1MB\nstderr_logfile_maxbytes=1MB\ndepend=channel\n";
    }
    private String run(boolean acceptStopped, String... args) throws Exception {
        List<String> command = new ArrayList<>(); command.add(supervisor); Collections.addAll(command, args);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!process.waitFor(50, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IOException("Supervisor 操作超时"); }
        String text = new String(readAll(process.getInputStream()), StandardCharsets.UTF_8);
        if (process.exitValue() != 0 && !acceptStopped) throw new IOException(text.trim());
        return text;
    }
    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(); byte[] b = new byte[1024]; int n;
        while ((n = in.read(b)) != -1) out.write(b, 0, n); return out.toByteArray();
    }
}
