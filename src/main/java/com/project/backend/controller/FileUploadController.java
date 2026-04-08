package com.project.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/upload")
@CrossOrigin
public class FileUploadController {

    private final String UPLOAD_DIR = "bot_files/";

    @PostMapping("/{playerId}")
    public ResponseEntity<Map<String, Object>> uploadAndCompileBot(
            @PathVariable String playerId,
            @RequestParam("file") MultipartFile file) {

        Map<String, Object> response = new HashMap<>();

        if (file.isEmpty() || file.getOriginalFilename() == null || !file.getOriginalFilename().endsWith(".java")) {
            response.put("success", false);
            response.put("message", "必须上传 .java 格式的源码文件！");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            File dir = new File(UPLOAD_DIR + playerId);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            Path javaFilePath = Paths.get(dir.getAbsolutePath(), "Main.java");
            Files.write(javaFilePath, file.getBytes());

            ProcessBuilder pb = new ProcessBuilder("javac", "-encoding", "UTF-8", "Main.java");
            pb.directory(dir);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder compileOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    compileOutput.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(10, TimeUnit.SECONDS);

            if (!finished) {
                process.destroy();
                response.put("success", false);
                response.put("message", "编译超时（超过10秒），请检查代码是否存在死循环或异常宏定义！");
                return ResponseEntity.status(500).body(response);
            }

            if (process.exitValue() == 0) {
                response.put("success", true);
                response.put("message", "玩家 " + playerId + " 的 Bot 上传并编译成功！");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "编译失败，请检查语法错误！");
                response.put("compileError", compileOutput.toString());
                return ResponseEntity.status(400).body(response);
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "服务器处理异常: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}