package cn.suhoan.starlight.service.search;

import java.util.ArrayList;
import java.util.List;

/**
 * 多关键词搜索评分器。
 * <p>
 * 评分规则（得分越高排名越前）：
 * <ol>
 *   <li>完全短语匹配：标题命中 +10000，正文命中 +5000</li>
 *   <li>每个独立关键词命中：标题命中 +200，正文命中 +100</li>
 *   <li>关键词邻近度奖励：匹配位置之间平均距离越小，得分越高（最高 +1000）</li>
 *   <li>关键词顺序奖励：关键词出现顺序与查询顺序一致时 +500</li>
 * </ol>
 *
 * @author suhoan
 */
public final class NoteSearchScorer {

    private NoteSearchScorer() {
    }

    /**
     * 计算一条笔记针对多个关键词的综合得分。
     *
     * @param title      笔记标题
     * @param plainText  笔记纯文本内容
     * @param keywords   搜索关键词列表（已按空格拆分，且已转小写）
     * @param rawQuery   原始搜索字符串（用于完整短语匹配检测）
     * @return 综合得分（非负整数）
     */
    public static int score(String title, String plainText, List<String> keywords, String rawQuery) {
        if (keywords == null || keywords.isEmpty()) {
            return 0;
        }

        String lowerTitle = title == null ? "" : title.toLowerCase();
        String lowerText = plainText == null ? "" : plainText.toLowerCase();
        String lowerQuery = rawQuery == null ? "" : rawQuery.toLowerCase().trim();

        int totalScore = 0;

        // ── 1. 完整短语匹配奖励 ──
        if (!lowerQuery.isEmpty() && keywords.size() > 1) {
            if (lowerTitle.contains(lowerQuery)) {
                totalScore += 10000;
            }
            if (lowerText.contains(lowerQuery)) {
                totalScore += 5000;
            }
        }

        // ── 2. 单关键词命中计分 ──
        int hitCount = 0;
        for (String kw : keywords) {
            if (lowerTitle.contains(kw)) {
                totalScore += 200;
                hitCount++;
            }
            if (lowerText.contains(kw)) {
                totalScore += 100;
                hitCount++;
            }
        }
        // 没有任何命中，直接返回 0
        if (hitCount == 0) {
            return 0;
        }

        // ── 3. 邻近度奖励（仅在正文中计算） ──
        if (keywords.size() > 1 && !lowerText.isEmpty()) {
            totalScore += proximityBonus(lowerText, keywords);
        }

        // ── 4. 顺序匹配奖励（仅在正文中计算） ──
        if (keywords.size() > 1 && !lowerText.isEmpty()) {
            totalScore += orderBonus(lowerText, keywords);
        }

        return totalScore;
    }

    /**
     * 计算关键词在文本中的邻近度奖励。
     * 取每个关键词首次出现位置，计算相邻位置之间距离的平均值，距离越小奖励越高。
     */
    private static int proximityBonus(String text, List<String> keywords) {
        List<Integer> positions = new ArrayList<>();
        for (String kw : keywords) {
            int idx = text.indexOf(kw);
            if (idx >= 0) {
                positions.add(idx);
            }
        }
        if (positions.size() < 2) {
            return 0;
        }
        // 按位置排序
        positions.sort(Integer::compareTo);
        long totalDistance = 0;
        for (int i = 1; i < positions.size(); i++) {
            totalDistance += positions.get(i) - positions.get(i - 1);
        }
        double avgDistance = (double) totalDistance / (positions.size() - 1);
        // 距离越小得分越高，上限 1000 分；当平均距离为 0 时获得满分
        // 使用衰减公式：bonus = 1000 / (1 + avgDistance / 50)
        return (int) (1000.0 / (1.0 + avgDistance / 50.0));
    }

    /**
     * 计算关键词在文本中的顺序匹配奖励。
     * 如果关键词按原始查询顺序出现在文本中（首次出现位置单调递增），则获得额外奖励。
     */
    private static int orderBonus(String text, List<String> keywords) {
        int lastPos = -1;
        boolean inOrder = true;
        for (String kw : keywords) {
            int idx = text.indexOf(kw);
            if (idx < 0) {
                // 该关键词未出现，无法判断顺序
                inOrder = false;
                break;
            }
            if (idx <= lastPos) {
                inOrder = false;
                break;
            }
            lastPos = idx;
        }
        return inOrder ? 500 : 0;
    }
}

