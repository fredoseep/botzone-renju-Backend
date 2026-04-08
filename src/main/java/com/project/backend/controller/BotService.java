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
        Process process = pb.start();

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
            writer.write(mapper.writeValueAsString(ioData) + "\n");
            writer.flush();
        }
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }

        process.waitFor();

        JsonNode root = mapper.readTree(output.toString());
        JsonNode responseNode = root.get("response");
        return new Move(responseNode.get("x").asInt(), responseNode.get("y").asInt());
    }
}