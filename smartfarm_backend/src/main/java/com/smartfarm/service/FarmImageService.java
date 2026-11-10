
package com.smartfarm.service;

import com.smartfarm.common.BizException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.UUID;

@Service
public class FarmImageService {

    private static final int MAX_SIZE = 5 * 1024 * 1024;

    // 图片暂存在操作系统临时目录，不进入 Git 项目
    private final Path uploadDir = Path.of(
            System.getProperty("java.io.tmpdir"),
            "smartfarm-disease-images"
    );

    /**
     * 上传图片，生成唯一 image_id
     */
    public String save(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new BizException("请选择图片");
        }

        try {
            byte[] bytes = file.getBytes();

            if (bytes.length > MAX_SIZE) {
                throw new BizException("图片不能超过5MB");
            }

            // 根据文件真实内容判断格式
            String extension;
            if (bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff) {
                extension = ".jpg";
            } else if (bytes.length >= 8
                    && (bytes[0] & 0xff) == 0x89
                    && bytes[1] == 0x50
                    && bytes[2] == 0x4e
                    && bytes[3] == 0x47) {
                extension = ".png";
            } else {
                throw new BizException("仅支持JPG和PNG图片");
            }

            // 验证能否正常解码
            var image = ImageIO.read(
                    new ByteArrayInputStream(bytes)
            );

            if (image == null) {
                throw new BizException("图片内容无效");
            }

            if (image.getWidth() > 10000
                    || image.getHeight() > 10000) {
                throw new BizException("图片分辨率过大");
            }

            Files.createDirectories(uploadDir);

            String imageId = UUID.randomUUID().toString();
            Path target = uploadDir.resolve(
                    imageId + extension
            );

            Files.write(
                    target,
                    bytes,
                    StandardOpenOption.CREATE_NEW
            );

            return imageId;

        } catch (IOException e) {
            throw new BizException("图片保存失败", e);
        }
    }

    /**
     * 根据 image_id 获取图片，供后续视觉识别使用。
     * 不直接作为公网图片下载接口开放。
     */
    public String getImageDataUrl(String imageId) {

        try {
            UUID.fromString(imageId);
        } catch (Exception e) {
            throw new BizException("无效的 image_id");
        }

        for (String extension : new String[]{".jpg", ".png"}) {

            Path path = uploadDir.resolve(
                    imageId + extension
            );

            if (Files.isRegularFile(path)) {
                try {
                    byte[] bytes = Files.readAllBytes(path);

                    String mime = extension.equals(".png")
                            ? "image/png"
                            : "image/jpeg";

                    return "data:" + mime + ";base64,"
                            + Base64.getEncoder()
                            .encodeToString(bytes);

                } catch (IOException e) {
                    throw new BizException("读取图片失败", e);
                }
            }
        }

        throw new BizException("图片不存在或已失效");
    }
}
