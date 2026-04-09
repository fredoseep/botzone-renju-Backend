package com.project.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.backend.entity.BotIOData;
import com.project.backend.entity.Move;
import org.springframework.stereotype.Service;
import java.io.*;

@Service
public class BotService {
    private final ObjectMapper mapper = new ObjectMapper();

    public Move getNextMove(String command, BotIOData ioData) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command.split(" "));
        pb.redirectErrorStream(true); // 这一句确保了 System.err 和 stderr 都能被完美捕获
        Process process = pb.start();

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), "UTF-8"))) {
            writer.write(mapper.writeValueAsString(ioData) + "\n");
            writer.flush();
        }

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        process.waitFor();
        String fullOutput = output.toString().trim();

        String jsonStr = null;
        String debugLogStr = "";

        // 【核心修改】：智能容错解析算法（从后往前寻找合法的 JSON）
        // 应对选手可能在输出 JSON 前用 System.out 打印了一堆垃圾文本的情况
        for (int i = fullOutput.length() - 1; i >= 0; i--) {
            // 找到一个潜在的 JSON 起始大括号
            if (fullOutput.charAt(i) == '{') {
                try {
                    String candidate = fullOutput.substring(i);
                    JsonNode root = mapper.readTree(candidate);

                    // 如果能成功解析为 JSON，且里面有 "response" 节点，说明这就是我们要找的最终输出！
                    if (root.has("response")) {
                        jsonStr = candidate;
                        // 大括号之前的所有内容，全是选手的乱打印，归入 debugLog
                        debugLogStr = fullOutput.substring(0, i).trim();

                        // 兼容 Botzone 标准：如果选手的 JSON 内部自带 "debug" 字段，也把它提取出来合并
                        if (root.has("debug")) {
                            String internalDebug = root.get("debug").asText();
                            if (internalDebug != null && !internalDebug.isEmpty()) {
                                debugLogStr = debugLogStr.isEmpty() ? internalDebug : (debugLogStr + "\n" + internalDebug);
                            }
                        }
                        break; // 找到了合法的，停止往前搜索
                    }
                } catch (Exception ignored) {
                    // 试着解析失败了（说明只是普通的文本里带了个 '{'），继续往前找
                }
            }
        }

        if (jsonStr == null) {
            throw new RuntimeException("Bot 未输出合法的 JSON 响应。控制台输出为:\n" + fullOutput);
        }

        // 解析出最终的坐标
        JsonNode root = mapper.readTree(jsonStr);
        JsonNode responseNode = root.get("response");
        Move move = new Move(responseNode.get("x").asInt(), responseNode.get("y").asInt());

        // 将合并好的调试信息带回
        move.debugLog = debugLogStr;

        return move;
    }
}