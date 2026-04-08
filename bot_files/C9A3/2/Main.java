import java.util.*;
import org.json.simple.JSONValue;

public class Main {

    static class GomokuAI {
        public static final int SIZE = 15;
        public static final int EMPTY = 0;

        // 棋盘数据
        private int[][] board = new int[SIZE][SIZE];
        private int myColor = 1;
        private int oppColor = 2;

        // 搜索深度，Botzone 通常 1 秒限时，深度设为 2 或 3 比较稳妥。如果超时请改为 2。
        private final int SEARCH_DEPTH = 3;

        public void placePiece(int x, int y, int color) {
            if (x >= 0 && x < SIZE && y >= 0 && y < SIZE) {
                board[x][y] = color;
            }
        }

        public void setColors(int myColor) {
            this.myColor = myColor;
            this.oppColor = (myColor == 1) ? 2 : 1;
        }

        // 核心接口：获取最佳落子位置
        public Map<String, Integer> getBestMove() {
            int[] bestMove = minimax(SEARCH_DEPTH, Integer.MIN_VALUE, Integer.MAX_VALUE, true);

            // 兜底策略：如果搜索失败（极少情况），退化为随机下在有邻居的空位
            if (bestMove[1] == -1 || bestMove[2] == -1) {
                List<int[]> candidates = generateCandidates();
                if (!candidates.isEmpty()) {
                    bestMove[1] = candidates.get(0)[0];
                    bestMove[2] = candidates.get(0)[1];
                }
            }

            Map<String, Integer> map = new HashMap<>();
            map.put("x", bestMove[1]);
            map.put("y", bestMove[2]);
            return map;
        }

        // 极小极大搜索 + Alpha-Beta 剪枝
        private int[] minimax(int depth, int alpha, int beta, boolean isMaximizing) {
            List<int[]> candidates = generateCandidates();

            // 如果到达叶子节点或者没有空位，返回局面评估分数
            if (depth == 0 || candidates.isEmpty()) {
                return new int[]{ evaluateBoard(), -1, -1 };
            }

            int bestX = -1;
            int bestY = -1;

            if (isMaximizing) {
                int maxEval = Integer.MIN_VALUE;
                for (int[] move : candidates) {
                    int x = move[0], y = move[1];
                    board[x][y] = myColor; // 假设我方落子
                    int eval = minimax(depth - 1, alpha, beta, false)[0];
                    board[x][y] = EMPTY;   // 撤销落子

                    if (eval > maxEval) {
                        maxEval = eval;
                        bestX = x;
                        bestY = y;
                    }
                    alpha = Math.max(alpha, eval);
                    if (beta <= alpha) break; // Beta 剪枝
                }
                return new int[]{ maxEval, bestX, bestY };
            } else {
                int minEval = Integer.MAX_VALUE;
                for (int[] move : candidates) {
                    int x = move[0], y = move[1];
                    board[x][y] = oppColor; // 假设敌方落子
                    int eval = minimax(depth - 1, alpha, beta, true)[0];
                    board[x][y] = EMPTY;    // 撤销落子

                    if (eval < minEval) {
                        minEval = eval;
                        bestX = x;
                        bestY = y;
                    }
                    beta = Math.min(beta, eval);
                    if (beta <= alpha) break; // Alpha 剪枝
                }
                return new int[]{ minEval, bestX, bestY };
            }
        }

        // 启发式搜索：只考虑已有棋子周围 1~2 格的空位，极大提高搜索速度
        private List<int[]> generateCandidates() {
            List<int[]> candidates = new ArrayList<>();
            for (int i = 0; i < SIZE; i++) {
                for (int j = 0; j < SIZE; j++) {
                    if (board[i][j] == EMPTY && hasNeighbor(i, j)) {
                        candidates.add(new int[]{i, j});
                    }
                }
            }
            return candidates;
        }

        private boolean hasNeighbor(int x, int y) {
            for (int i = Math.max(0, x - 2); i <= Math.min(SIZE - 1, x + 2); i++) {
                for (int j = Math.max(0, y - 2); j <= Math.min(SIZE - 1, y + 2); j++) {
                    if (i == x && j == y) continue;
                    if (board[i][j] != EMPTY) return true;
                }
            }
            return false;
        }

        // 局面评估函数：我方得分 - 敌方得分
        private int evaluateBoard() {
            return evaluateColor(myColor) - evaluateColor(oppColor);
        }

        // 评估特定颜色的棋面得分
        private int evaluateColor(int color) {
            int score = 0;
            // 4个方向：横、竖、右斜、左斜
            int[] dx = {1, 0, 1, 1};
            int[] dy = {0, 1, 1, -1};

            // 判断当前评估的颜色是否为对手颜色，用于防守权重判定
            boolean isOpponent = (color == oppColor);

            for (int i = 0; i < SIZE; i++) {
                for (int j = 0; j < SIZE; j++) {
                    if (board[i][j] == color) {
                        for (int dir = 0; dir < 4; dir++) {
                            // 为了避免重复计算，只从一条线的起点开始计算
                            int prevX = i - dx[dir];
                            int prevY = j - dy[dir];
                            if (prevX >= 0 && prevX < SIZE && prevY >= 0 && prevY < SIZE && board[prevX][prevY] == color) {
                                continue;
                            }

                            int count = 1;
                            int blocks = 0; // 被挡住的端点数 (0~2)

                            // 检查前面的端点
                            if (prevX < 0 || prevX >= SIZE || prevY < 0 || prevY >= SIZE || board[prevX][prevY] != EMPTY) {
                                blocks++;
                            }

                            // 向后延伸数连续的棋子
                            int nextX = i + dx[dir];
                            int nextY = j + dy[dir];
                            while (nextX >= 0 && nextX < SIZE && nextY >= 0 && nextY < SIZE && board[nextX][nextY] == color) {
                                count++;
                                nextX += dx[dir];
                                nextY += dy[dir];
                            }

                            // 检查后面的端点
                            if (nextX < 0 || nextX >= SIZE || nextY < 0 || nextY >= SIZE || board[nextX][nextY] != EMPTY) {
                                blocks++;
                            }

                            // 传入 isOpponent 参数进行针对性打分
                            score += getShapeScore(count, blocks, isOpponent);
                        }
                    }
                }
            }
            return score;
        }

        // 核心灵魂：棋型打分，带有防守强化逻辑
        private int getShapeScore(int count, int blocks, boolean isOpponent) {
            if (blocks == 2 && count < 5) return 0; // 死棋无意义
            if (count >= 5) return 100000;          // 连五，赢定

            int baseScore = 0;
            if (count == 4) baseScore = (blocks == 0) ? 10000 : 1000; // 活四 10000，冲四 1000
            if (count == 3) baseScore = (blocks == 0) ? 1000 : 100;   // 活三 1000，眠三 100
            if (count == 2) baseScore = (blocks == 0) ? 100 : 10;     // 活二 100，眠二 10
            if (count == 1) baseScore = (blocks == 0) ? 10 : 1;

            // 【防守强化核心逻辑】
            if (isOpponent) {
                // 对手的活三和冲四非常危险，距离赢只差一步/两步！
                // 将它们的分数翻倍，迫使 AI 优先拦截对手
                if (count == 3 && blocks == 0) return baseScore * 2;
                if (count == 4 && blocks == 1) return baseScore * 2;

                // 对于其他普通棋型，整体防守权重提高 50%
                return (int) (baseScore * 1.5);
            }

            return baseScore;
        }
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        if (!scanner.hasNextLine()) return;
        String inputFull = scanner.nextLine();

        Map<String, List<Map<String, Long>>> input = (Map<String, List<Map<String, Long>>>) JSONValue.parse(inputFull);
        List<Map<String, Long>> requests = input.get("requests");
        List<Map<String, Long>> responses = input.get("responses");

        GomokuAI ai = new GomokuAI();
        Map<String, Integer> outputObj = new HashMap<>();

        // 1. 判断是否是第一回合以及分配颜色
        boolean isFirstMove = (responses == null || responses.isEmpty());
        if (isFirstMove && requests.size() == 1 && requests.get(0).get("x").intValue() == -1) {
            // 对手传了 -1, -1，说明我们是黑棋（先手）
            ai.setColors(1);
            // 黑棋第一步直接下天元 (7, 7)
            outputObj.put("x", 7);
            outputObj.put("y", 7);
            Map<String, Object> responseContainer = new HashMap<>();
            responseContainer.put("response", outputObj);
            System.out.print(JSONValue.toJSONString(responseContainer));
            return;
        }

        // 我们是白棋（后手），或者游戏已经进行中
        int myColor = (requests.get(0).get("x").intValue() == -1) ? 1 : 2;
        int oppColor = (myColor == 1) ? 2 : 1;
        ai.setColors(myColor);

        // 2. 还原棋盘历史状态
        for (int i = 0; i < requests.size(); i++) {
            int rx = requests.get(i).get("x").intValue();
            int ry = requests.get(i).get("y").intValue();
            if (rx >= 0 && ry >= 0) {
                ai.placePiece(rx, ry, oppColor); // 对手落子
            }
            if (i < responses.size()) {
                int mx = responses.get(i).get("x").intValue();
                int my = responses.get(i).get("y").intValue();
                if (mx >= 0 && my >= 0) {
                    ai.placePiece(mx, my, myColor); // 我们落子
                }
            }
        }

        // 3. 计算最佳落子
        Map<String, Integer> bestMove = ai.getBestMove();

        // 4. 封装并输出 JSON
        Map<String, Object> responseContainer = new HashMap<>();
        responseContainer.put("response", bestMove);
        System.out.print(JSONValue.toJSONString(responseContainer));
    }
}