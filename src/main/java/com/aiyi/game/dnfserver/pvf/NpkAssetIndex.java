package com.aiyi.game.dnfserver.pvf;

import com.xiaoyouma.dnf.parser.npk.enums.ImgVersion;
import com.xiaoyouma.dnf.parser.npk.handle.HandleFactory;
import com.xiaoyouma.dnf.parser.npk.handle.IHandle;
import com.xiaoyouma.dnf.parser.npk.model.NpkImg;
import com.xiaoyouma.dnf.parser.npk.util.NpkHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A complete, immutable index, so a new resource pack can replace the old one atomically. */
final class NpkAssetIndex {
    private static final Logger LOG = LoggerFactory.getLogger(NpkAssetIndex.class);
    private static final byte[] MAGIC = "NeoplePack_Bill".getBytes(StandardCharsets.US_ASCII);
    private static final long TABLE_ENTRY_BYTES = 264;
    private static final int MAX_INDEX_ENTRIES = 2_000_000;
    private static final long MAX_IMG_BYTES = 64L * 1024 * 1024;

    private final Path root;
    private final Map<String, Entry> entries;
    private final int packCount;
    private final String revision;
    private final boolean uploaded;

    private NpkAssetIndex(Path root, Map<String, Entry> entries, int packCount, String revision, boolean uploaded) {
        this.root = root;
        this.entries = entries;
        this.packCount = packCount;
        this.revision = revision;
        this.uploaded = uploaded;
    }

    static NpkAssetIndex empty() {
        return new NpkAssetIndex(null, Collections.emptyMap(), 0, "none", false);
    }

    static NpkAssetIndex build(Path source, String revision, boolean uploaded, boolean strict) throws IOException {
        if (!Files.isDirectory(source)) return empty();
        // The integrated deployment may expose the existing directory via a symlink.
        Path root = source.toRealPath();
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(root) && ImagePackStorage.isInternalDirectory(root.relativize(dir))) return FileVisitResult.SKIP_SUBTREE;
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (Files.isRegularFile(file) && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".npk")) files.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(Comparator.comparing(path -> root.relativize(path).toString().toLowerCase(Locale.ROOT)));
        Map<String, Entry> entries = new HashMap<>();
        int count = 0;
        for (Path file : files) {
            try {
                Map<String, Entry> packEntries = readPack(root, file);
                if ((long) entries.size() + packEntries.size() > MAX_INDEX_ENTRIES) {
                    throw new IOException("NPK 图片索引数量过多");
                }
                packEntries.forEach(entries::putIfAbsent);
                count++;
            } catch (IOException | RuntimeException e) {
                if (strict) throw new IOException("NPK 文件无效：" + file.getFileName() + "（" + e.getMessage() + "）", e);
                LOG.warn("忽略无效 NPK 文件 {}: {}", file, e.getMessage());
            }
        }
        return new NpkAssetIndex(root, Collections.unmodifiableMap(entries), count, revision, uploaded);
    }

    private static Map<String, Entry> readPack(Path root, Path file) throws IOException {
        long size = Files.size(file);
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            byte[] header = new byte[16];
            in.readFully(header);
            for (int i = 0; i < MAGIC.length; i++) {
                if (header[i] != MAGIC[i]) throw new IOException("文件头不正确");
            }
            if (header[15] != 0) throw new IOException("文件头不正确");
            long count = Integer.toUnsignedLong(Integer.reverseBytes(in.readInt()));
            if (count > MAX_INDEX_ENTRIES || count > (size - 20) / TABLE_ENTRY_BYTES) {
                throw new IOException("图片索引数量不正确");
            }
            long dataStart = 20 + count * TABLE_ENTRY_BYTES;
            Map<String, Entry> result = new HashMap<>();
            byte[] nameBytes = new byte[256];
            for (long i = 0; i < count; i++) {
                long offset = Integer.toUnsignedLong(Integer.reverseBytes(in.readInt()));
                long length = Integer.toUnsignedLong(Integer.reverseBytes(in.readInt()));
                in.readFully(nameBytes);
                String name = normalizeName(NpkHelper.decryptBytesToStr(nameBytes));
                if (length == 0) continue;
                if (name.isEmpty() || offset < dataStart || offset > size || length > size - offset) {
                    throw new IOException("图片索引超出文件范围");
                }
                result.putIfAbsent(name, new Entry(root.relativize(file), offset, length));
            }
            return result;
        } catch (EOFException e) {
            throw new IOException("文件不完整", e);
        }
    }

    NpkAssetIndex at(Path newRoot) {
        return new NpkAssetIndex(newRoot, entries, packCount, revision, uploaded);
    }

    int getPackCount() { return packCount; }
    int getImageCount() { return entries.size(); }
    String getRevision() { return revision; }
    boolean isUploaded() { return uploaded; }

    NpkImg loadImg(String name) throws IOException {
        Entry entry = entries.get(normalizeName(name));
        if (entry == null || entry.length > MAX_IMG_BYTES) return null;
        try (FileChannel channel = FileChannel.open(root.resolve(entry.file), StandardOpenOption.READ)) {
            channel.position(entry.offset);
            LimitedInputStream check = limitedStream(channel, entry.length);
            Header header = readHeader(check);
            if (header == null || !validFrames(check, header)) return null;

            // Validate frame lengths before the parser allocates buffers. Rewind to parse
            // the same IMG with the existing decoder once it is known to be bounded.
            channel.position(entry.offset);
            LimitedInputStream input = limitedStream(channel, entry.length);
            header = readHeader(input);
            if (header == null) return null;
            NpkImg img = NpkImg.builder().magicNumber(header.magic).indexSize(header.indexSize)
                    .reserve(header.reserve).version(header.version).indexCount(header.count).build();
            IHandle handle = HandleFactory.of(ImgVersion.of(header.version));
            if (handle != null) handle.readStream(input, img);
            return img;
        }
    }

    private static LimitedInputStream limitedStream(FileChannel channel, long length) {
        return new LimitedInputStream(new BufferedInputStream(Channels.newInputStream(channel)), length);
    }

    private static Header readHeader(InputStream input) throws IOException {
        ByteArrayOutputStream magicBytes = new ByteArrayOutputStream();
        boolean terminated = false;
        for (int i = 0; i < 64; i++) {
            int value = input.read();
            if (value < 0) throw new EOFException();
            if (value == 0) { terminated = true; break; }
            magicBytes.write(value);
        }
        if (!terminated) return null;
        String magic = new String(magicBytes.toByteArray(), StandardCharsets.US_ASCII);
        DataInputStream data = new DataInputStream(input);
        int size = Integer.reverseBytes(data.readInt());
        int reserve = 0;
        if (magic.startsWith("Neople Image File")) {
            data.readUnsignedShort();
        } else if (magic.startsWith("Neople Img File")) {
            reserve = Integer.reverseBytes(data.readInt());
        } else {
            return null;
        }
        int version = Integer.reverseBytes(data.readInt());
        int count = Integer.reverseBytes(data.readInt());
        if ((version != 1 && version != 2) || count < 0 || count > 16384) return null;
        return new Header(magic, size, reserve, version, count);
    }

    private static boolean validFrames(LimitedInputStream input, Header header) throws IOException {
        DataInputStream data = new DataInputStream(input);
        long imageData = 0;
        for (int i = 0; i < header.count; i++) {
            int type = Integer.reverseBytes(data.readInt());
            if (type == 0x11) {
                int target = Integer.reverseBytes(data.readInt());
                if (target < 0 || target >= header.count) return false;
                continue;
            }
            int compression = Integer.reverseBytes(data.readInt());
            int width = Integer.reverseBytes(data.readInt());
            int height = Integer.reverseBytes(data.readInt());
            int length = Integer.reverseBytes(data.readInt());
            data.readInt(); // x
            data.readInt(); // y
            data.readInt(); // frame width
            data.readInt(); // frame height
            if (width < 0 || height < 0 || width > 8192 || height > 8192 ||
                    (long) width * height > 16_777_216 || length < 0) return false;
            long bytes = compression == 0x05 ? (long) width * height * (type == 0x10 ? 4 : 2) : length;
            if (bytes > MAX_IMG_BYTES || imageData > MAX_IMG_BYTES - bytes) return false;
            imageData += bytes;
            if (header.version == 1) skipFully(input, bytes);
        }
        return header.version == 1 || imageData <= input.remaining;
    }

    private static void skipFully(InputStream input, long bytes) throws IOException {
        while (bytes > 0) {
            long skipped = input.skip(bytes);
            if (skipped == 0) {
                if (input.read() < 0) throw new EOFException();
                skipped = 1;
            }
            bytes -= skipped;
        }
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    private static final class Entry {
        final Path file;
        final long offset;
        final long length;
        Entry(Path file, long offset, long length) { this.file = file; this.offset = offset; this.length = length; }
    }

    private static final class Header {
        final String magic;
        final int indexSize, reserve, version, count;
        Header(String magic, int indexSize, int reserve, int version, int count) {
            this.magic = magic; this.indexSize = indexSize; this.reserve = reserve; this.version = version; this.count = count;
        }
    }

    private static final class LimitedInputStream extends InputStream {
        private final InputStream input;
        private long remaining;
        LimitedInputStream(InputStream input, long remaining) { this.input = input; this.remaining = remaining; }
        @Override public int read() throws IOException {
            if (remaining == 0) return -1;
            int value = input.read();
            if (value != -1) remaining--;
            return value;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) return 0;
            if (remaining == 0) return -1;
            int n = input.read(bytes, offset, (int) Math.min(remaining, length));
            if (n > 0) remaining -= n;
            return n;
        }
        @Override public long skip(long count) throws IOException {
            if (count <= 0 || remaining == 0) return 0;
            long n = input.skip(Math.min(remaining, count));
            remaining -= n;
            return n;
        }
        @Override public void close() throws IOException { input.close(); }
    }
}
