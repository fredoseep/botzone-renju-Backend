package com.project.backend.entity;

import com.project.backend.entity.Move;

import java.util.ArrayList;
import java.util.List;

public class GameRoom {
    public String roomId;
    public String hostClientId;
    public int[][] board = new int[15][15];
    public List<GameLog> logs = new ArrayList<>();
    public List<Move> history1 = new ArrayList<>();
    public List<Move> history2 = new ArrayList<>();
    public int currentPlayer = 1;
    public int winner = 0;

    public boolean p1Ready = false;
    public boolean p2Ready = false;
    public String p1Lang = "";
    public String p2Lang = "";

    public String errorMessage = null;

    public GameRoom(String roomId, String hostClientId) {
        this.roomId = roomId;
        this.hostClientId = hostClientId;
    }
    public void resetGame() {
        this.board = new int[15][15];
        this.history1.clear();
        this.history2.clear();
        this.currentPlayer = 1; // 默认重新由黑子先手
        this.winner = 0;
        this.errorMessage = null;
        this.logs.clear();
    }
}