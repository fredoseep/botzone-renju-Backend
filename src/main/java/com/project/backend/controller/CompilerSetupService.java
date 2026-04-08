package com.project.backend.controller; // 放在你合适的包下

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class CompilerSetupService {

    public static final String COMPILER_DIR = "bot_compiler";

    @PostConstruct
    public void initCompiler() {
        File compilerDir = new File(COMPILER_DIR);
        // 检查 g++.exe 是否已经解压过了
        File gppExe = new File(compilerDir, "mingw64/bin/g++.exe");

        if (gppExe.exists()) {
            System.out.println("✅ 内置 C++ 编译器已就绪！路径: " + gppExe.getAbsolutePath());
            return;
        }

        System.out.println("⏳ 首次启动，正在从压缩包释放内置 C++ 编译器，请耐心等待...");
        try {
            compilerDir.mkdirs();
            InputStream is = getClass().getResourceAsStream("/mingw64.zip");
            if (is == null) {
                System.err.println("❌ 找不到 mingw64.zip，请确保它在 src/main/resources 下！");
                return;
            }

            unzip(is, compilerDir);
            System.out.println("✅ 内置编译器释放完成！");

        } catch (Exception e) {
            System.err.println("❌ 释放编译器失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void unzip(InputStream is, File destDir) throws IOException {
        byte[] buffer = new byte[8192];
        try (ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = new File(destDir, zipEntry.getName());

                if (!newFile.getCanonicalPath().startsWith(destDir.getCanonicalPath() + File.separator)) {
                    throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
                }

                if (zipEntry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    newFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }
}