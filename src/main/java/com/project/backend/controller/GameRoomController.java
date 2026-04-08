package com.project.backend.controller;

import com.project.backend.entity.BotIOData;
import com.project.backend.entity.GameLog;
import com.project.backend.entity.GameRoom;
import com.project.backend.entity.Move;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/room")
@CrossOrigin
public class GameRoomController {

    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final String UPLOAD_DIR = "bot_files/";

    @Autowired
    private BotService botService;

    @PostMapping("/create")
    public String createRoom(@RequestParam String clientId) {
        String roomId = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        rooms.put(roomId, new GameRoom(roomId, clientId)); // 绑定房主身份
        return roomId;
    }

    @GetMapping("/{roomId}")
    public GameRoom getRoomState(@PathVariable String roomId) {
        return rooms.get(roomId);
    }

    /**
     * 接口 1：处理文件上传
     */
    @PostMapping("/{roomId}/upload/{playerId}")
    public ResponseEntity<String> uploadAndCompile(
            @PathVariable String roomId,
            @PathVariable int playerId,
            @RequestParam("file") MultipartFile file) {

        GameRoom room = rooms.get(roomId);
        if (room == null) return ResponseEntity.badRequest().body("房间不存在");

        try {
            File dir = new File(UPLOAD_DIR + roomId + "/" + playerId);
            if (!dir.exists()) dir.mkdirs();

            String originalName = file.getOriginalFilename();
            boolean isCpp = originalName != null && originalName.toLowerCase().endsWith(".cpp");
            String fileName = isCpp ? "main.cpp" : "Main.java";

            Path filePath = Paths.get(dir.getAbsolutePath(), fileName);
            Files.write(filePath, file.getBytes());

            return internalCompile(dir, room, playerId, isCpp ? "cpp" : "java");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("服务器异常: " + e.getMessage());
        }
    }

    /**
     * 接口 2：处理纯文本上传（用于前端“记住代码”功能）
     */
    @PostMapping("/{roomId}/upload-text/{playerId}")
    public ResponseEntity<String> uploadTextAndCompile(
            @PathVariable String roomId,
            @PathVariable int playerId,
            @RequestParam(defaultValue = "java") String lang,
            @RequestBody String codeContent) {

        GameRoom room = rooms.get(roomId);
        if (room == null) return ResponseEntity.badRequest().body("房间不存在");

        try {
            File dir = new File(UPLOAD_DIR + roomId + "/" + playerId);
            if (!dir.exists()) dir.mkdirs();

            String fileName = "cpp".equalsIgnoreCase(lang) ? "main.cpp" : "Main.java";
            Path filePath = Paths.get(dir.getAbsolutePath(), fileName);
            Files.write(filePath, codeContent.getBytes("UTF-8"));

            return internalCompile(dir, room, playerId, lang.toLowerCase());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("服务器异常: " + e.getMessage());
        }
    }

    /**
     * 抽离出的通用编译逻辑
     */
    private ResponseEntity<String> internalCompile(File dir, GameRoom room, int playerId, String lang) throws Exception {
        ProcessBuilder pb;
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        if ("java".equals(lang)) {
            File libFile = new File("libs/json-simple-1.1.1.jar");
            pb = new ProcessBuilder(
                    "javac", "-J-Dfile.encoding=UTF-8", "-J-Duser.language=en",
                    "-cp", libFile.getAbsolutePath(), "-encoding", "UTF-8", "Main.java"
            );
        } else {
            String execName = isWindows ? "bot.exe" : "bot";

            File gppExe = new File(CompilerSetupService.COMPILER_DIR, "mingw64/bin/g++.exe");
            if (!gppExe.exists()) {
                return ResponseEntity.status(500).body("服务器内部缺失 C++ 编译器组件，请重启服务器释放环境！");
            }

            File cppDepsDir = new File("libs/cpp_deps");
            File jsonCppSource = new File(cppDepsDir, "jsoncpp.cpp");

            pb = new ProcessBuilder(
                    gppExe.getAbsolutePath(),
                    "main.cpp",
                    jsonCppSource.getAbsolutePath(),
                    "-I" + cppDepsDir.getAbsolutePath(),
                    "-o", execName,
                    "-O2", "-std=c++11",
                    "-static"
            );

            Map<String, String> env = pb.environment();
            String binPath = new File(CompilerSetupService.COMPILER_DIR, "mingw64/bin").getAbsolutePath();
            env.put("Path", binPath + File.pathSeparator + env.getOrDefault("Path", ""));

            pb.directory(dir);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder compileOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) compileOutput.append(line).append("\n");
            }

            boolean finished = process.waitFor(15, TimeUnit.SECONDS);

            if (finished && process.exitValue() == 0) {
                if (playerId == 1) { room.p1Ready = true; room.p1Lang = lang; }
                else { room.p2Ready = true; room.p2Lang = lang; }
                return ResponseEntity.ok("编译成功");
            } else {
                if (!finished) process.destroy();
                return ResponseEntity.status(400).body(compileOutput.toString());
            }
        }

        pb.directory(dir);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder compileOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) compileOutput.append(line).append("\n");
        }

        boolean finished = process.waitFor(15, TimeUnit.SECONDS);

        if (finished && process.exitValue() == 0) {
            if (playerId == 1) { room.p1Ready = true; room.p1Lang = lang; }
            else { room.p2Ready = true; room.p2Lang = lang; }
            return ResponseEntity.ok("编译成功");
        } else {
            if (!finished) process.destroy();
            return ResponseEntity.status(400).body(compileOutput.toString());
        }
    }

    @PostMapping("/{roomId}/next-turn")
    public GameRoom nextTurn(@PathVariable String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null || room.winner != 0 || room.errorMessage != null) {
            return room;
        }
        long startTime = System.currentTimeMillis();

        try {
            File botDir = new File(UPLOAD_DIR + roomId + "/" + room.currentPlayer);
            String commandToRun;
            String currentLang = (room.currentPlayer == 1) ? room.p1Lang : room.p2Lang;
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

            if ("java".equals(currentLang)) {
                File libFile = new File("libs/json-simple-1.1.1.jar");
                String cp = botDir.getAbsolutePath() + File.pathSeparator + libFile.getAbsolutePath();
                commandToRun = "java -cp \"" + cp + "\" Main";
            } else if ("cpp".equals(currentLang)) {
                String execName = isWindows ? "bot.exe" : "bot";
                commandToRun = new File(botDir, execName).getAbsolutePath();
            } else {
                throw new RuntimeException("未知的语言类型或代码未编译");
            }

            BotIOData ioData = new BotIOData();
            if (room.currentPlayer == 1) {
                ioData.requests.add(new Move(-1, -1));
                ioData.requests.addAll(room.history2); // 对手（白子）的记录
                ioData.responses.addAll(room.history1); // 自己（黑子）的记录
            } else {
                ioData.requests.addAll(room.history1); // 对手（黑子）的记录
                ioData.responses.addAll(room.history2); // 自己（白子）的记录
            }

            Move nextMove = botService.getNextMove(commandToRun, ioData);

            long responseTime = System.currentTimeMillis() - startTime;

            if (nextMove.x < 0 || nextMove.x >= 15 || nextMove.y < 0 || nextMove.y >= 15) {
                throw new RuntimeException("Bot 输出了越界坐标: (" + nextMove.x + ", " + nextMove.y + ")");
            }
            if (room.board[nextMove.x][nextMove.y] != 0) {
                throw new RuntimeException("该位置已有棋子: (" + nextMove.x + ", " + nextMove.y + ")");
            }

            int currentStep = room.history1.size() + room.history2.size() + 1;
            String colorName = (room.currentPlayer == 1) ? "Black" : "White";

            GameLog log = new GameLog(
                    currentStep,
                    room.currentPlayer,
                    colorName,
                    nextMove.x,
                    nextMove.y,
                    responseTime
            );
            room.logs.add(log);

            room.board[nextMove.x][nextMove.y] = room.currentPlayer;
            if (room.currentPlayer == 1) {
                room.history1.add(nextMove);
            } else {
                room.history2.add(nextMove);
            }

            int winner = checkWin(room.board);
            if (winner != 0) {
                room.winner = winner;
            } else {
                room.currentPlayer = (room.currentPlayer == 1) ? 2 : 1;
            }

        } catch (Exception e) {
            room.errorMessage = "玩家 " + room.currentPlayer + " 运行出错: " + e.getMessage();
            room.winner = (room.currentPlayer == 1) ? 2 : 1;
        }

        return room;
    }

    private int checkWin(int[][] board) {
        int size = 15;
        int[][] directions = { {1, 0}, {0, 1}, {1, 1}, {-1, 1} };
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                int player = board[x][y];
                if (player == 0) continue;
                for (int[] dir : directions) {
                    int dx = dir[0], dy = dir[1], count = 1;
                    for (int step = 1; step < 5; step++) {
                        int nx = x + dx * step, ny = y + dy * step;
                        if (nx < 0 || nx >= size || ny < 0 || ny >= size || board[nx][ny] != player) break;
                        count++;
                    }
                    if (count == 5) return player;
                }
            }
        }
        return 0;
    }

    @PostMapping("/{roomId}/restart")
    public ResponseEntity<String> restartGame(@PathVariable String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) return ResponseEntity.badRequest().body("房间不存在");
        room.resetGame();
        return ResponseEntity.ok("房间已重置");
    }
}