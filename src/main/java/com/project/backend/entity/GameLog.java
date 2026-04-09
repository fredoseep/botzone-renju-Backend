package com.project.backend.entity;

public class GameLog {
    public int step;          // 全局步骤序号
    public int player;        // 玩家编号 (1 或 2)
    public String color;      // 颜色描述 ("Black" 或 "White")
    public int x;
    public int y;
    public long responseTime; // 响应时间 (毫秒)
    public String debugLog;

    public GameLog(int step, int player, String color, int x, int y, long responseTime, String debugLog) {
        this.step = step;
        this.player = player;
        this.color = color;
        this.x = x;
        this.y = y;
        this.responseTime = responseTime;
        this.debugLog = debugLog;
    }
}
