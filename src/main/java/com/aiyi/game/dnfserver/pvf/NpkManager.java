package com.aiyi.game.dnfserver.pvf;

import com.xiaoyouma.dnf.parser.npk.model.NpkImg;
import com.xiaoyouma.dnf.parser.npk.model.NpkTexture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * NPK管理器
 * @author xiatian
 */
@Component
public class NpkManager {

    protected static final Logger logger = LoggerFactory.getLogger(NpkManager.class);

    @Resource
    private ImagePackStorage imagePackStorage;

    /**
     * 获取图片字节数组
     * @param path 图片路径
     * @param index 图片索引
     * @return 图片字节数组
     */
    public byte[] getImageBytes(String path, int index){
        NpkTexture texture = getTexture(path, index);
        if (texture == null) return new byte[0];
        try {
            return texture.toPngBytes();
        }catch (Exception e){
            return new byte[0];
        }
    }

    /** Render an IMG frame with its original transparent canvas and offsets. */
    public byte[] getImageFrameBytes(String path, int index) {
        NpkTexture texture = getTexture(path, index);
        if (texture == null) return new byte[0];
        try {
            Set<NpkTexture> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            while (texture.isLink()) {
                if (!visited.add(texture)) return new byte[0];
                texture = texture.getLinkTarget();
                if (texture == null) return new byte[0];
            }
            int width = texture.getFrameWidth(), height = texture.getFrameHeight();
            int imageWidth = texture.getWidth(), imageHeight = texture.getHeight();
            if (imageWidth <= 0 || imageHeight <= 0 || imageWidth > 2048 || imageHeight > 2048 ||
                    (long) imageWidth * imageHeight > 4194304) return new byte[0];
            byte[] png = texture.toPngBytes();
            if (width <= 0 || height <= 0) return png;
            if (width > 2048 || height > 2048 || (long) width * height > 4194304) return new byte[0];
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null) return new byte[0];
            BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = canvas.createGraphics();
            try { graphics.drawImage(image, texture.getX(), texture.getY(), null); }
            finally { graphics.dispose(); }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            return ImageIO.write(canvas, "png", output) ? output.toByteArray() : new byte[0];
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private NpkTexture getTexture(String path, int index) {
        if (path == null || index < 0) return null;
        try {
            NpkImg img = imagePackStorage.loadImg(path);
            NpkTexture[] textures = img == null ? null : img.getTextures();
            return textures == null || index >= textures.length ? null : textures[index];
        } catch (Exception e) {
            return null;
        }
    }
}
