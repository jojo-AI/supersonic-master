package com.tencent.supersonic.chat.server.util;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 修正版高速公路意图识别器
 * 修复了"张三的销售总额是多少"被误判为问策的问题
 */
@Slf4j
@Component
public class HighwayIntentRecognizer {

    public enum IntentType {
        DATA_QUERY,      // 问数
        KNOWLEDGE_QUERY, // 问策
        UNKNOWN
    }

    @Data
    public static class RecognitionResult {
        private IntentType intentType;
        private double confidence;
        private String reason;
        private boolean requiresHumanReview;

        public RecognitionResult(IntentType intentType, double confidence, String reason) {
            this.intentType = intentType;
            this.confidence = confidence;
            this.reason = reason;
            this.requiresHumanReview = confidence < 0.9;
        }

        public boolean isCertain() {
            return confidence >= 0.9;
        }
    }

    // 问数特征
    private static final Map<String, Double> DATA_FEATURES = new HashMap<String, Double>() {{
        // 统计计算类
        put("统计", 0.9);
        put("计算", 0.8);
        put("分析", 0.8);
        put("汇总", 0.7);
        put("对比", 0.8);
        put("比较", 0.7);

        // 排名趋势类
        put("排名", 0.9);
        put("排行", 0.8);
        put("趋势", 0.7);
        put("分布", 0.7);
        put("占比", 0.8);
        put("百分比", 0.7);

        // 具体统计指标
        put("总额", 0.9);
        put("总和", 0.9);
        put("合计", 0.8);
        put("总数", 0.8);
        put("数量", 0.8);
        put("金额", 0.8);
        put("数量", 0.8);
        put("频次", 0.7);
        put("频率", 0.7);

        // 时间相关
        put("同比", 1.0);
        put("环比", 1.0);
        put("增长率", 0.9);
        put("本月", 0.6);
        put("本周", 0.6);
        put("今年", 0.6);

        // 高速公路数据类
        put("稽核", 0.9);
        put("流水", 0.8);
        put("门架", 0.8);
        put("收费站", 0.7);
        put("通行", 0.7);
        put("交易", 0.7);
        put("车辆", 0.6);
        put("车牌", 0.6);

        // 报表图表类
        put("报表", 0.8);
        put("报告", 0.7);
        put("图表", 0.7);
    }};

    // 问策特征
    private static final Map<String, Double> KNOWLEDGE_FEATURES = new HashMap<String, Double>() {{
        // 疑问词类
        put("如何", 1.0);
        put("怎样", 0.9);
        put("怎么", 0.9);
        put("为什么", 0.9);
        put("为何", 0.9);
        put("什么", 0.9);
        put("哪些", 0.8);
        put("哪个", 0.8);
        put("哪里", 0.8);
        put("谁", 0.8);

        // 政策流程类
        put("政策", 1.0);
        put("规定", 0.9);
        put("标准", 0.8);
        put("流程", 0.9);
        put("步骤", 0.9);
        put("方法", 0.8);
        put("条件", 0.8);
        put("要求", 0.8);

        // 高速公路知识类
        put("绿通", 1.0);
        put("超载", 1.0);
        put("超速", 1.0);
        put("免费", 0.7);
        put("处罚", 0.9);
        put("扣分", 0.9);
        put("罚款", 0.9);

        // 信息查询类
        put("家庭住址", 1.0);
        put("地址", 0.9);
        put("车牌", 1.0);
        put("品牌", 0.8);
        put("颜色", 0.8);
        put("手机", 0.9);
        put("电话", 0.9);
    }};

    // 问数强信号
    private static final Set<String> DATA_STRONG_SIGNALS = new HashSet<>(Arrays.asList(
            "同比", "环比", "增长率", "排名", "排行", "占比", "报表", "柱状图", "折线图"
    ));

    // 问策强信号
    private static final Set<String> KNOWLEDGE_STRONG_SIGNALS = new HashSet<>(Arrays.asList(
            "如何", "怎样", "为什么", "是什么", "什么是", "车牌号", "家庭住址", "手机号", "身份证"
    ));

    // 正则模式
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("\\d+");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}");
    private static final Pattern COMPARISON_PATTERN = Pattern.compile("大于|小于|等于|高于|低于|超过|不低于|不超过");
    private static final Pattern PERSON_NAME_PATTERN = Pattern.compile("[张李王刘陈杨赵黄周吴徐孙胡朱高林何郭马罗].{1,2}");
    private static final Pattern LICENSE_PLATE_PATTERN = Pattern.compile(
            "[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领][A-Z][A-Z0-9]{4,5}[A-Z0-9挂学警港澳]");

    /**
     * 主识别方法
     */
    public RecognitionResult recognize(String query) {
        if (!StringUtils.hasText(query)) {
            return new RecognitionResult(IntentType.UNKNOWN, 0.0, "空查询");
        }

        query = query.trim();
        String lowerQuery = query.toLowerCase();

        // 1. 强信号匹配
        RecognitionResult strongResult = checkStrongSignals(lowerQuery);
        if (strongResult != null) {
            return strongResult;
        }

        // 2. 检查特殊模式
        RecognitionResult specialPatternResult = checkSpecialPatterns(lowerQuery);
        if (specialPatternResult != null) {
            return specialPatternResult;
        }

        // 3. 特征匹配
        return matchByFeatures(lowerQuery, query);
    }

    /**
     * 检查强信号
     */
    private RecognitionResult checkStrongSignals(String query) {
        for (String signal : DATA_STRONG_SIGNALS) {
            if (query.contains(signal)) {
                return new RecognitionResult(IntentType.DATA_QUERY, 0.99,
                        String.format("强信号词: %s", signal));
            }
        }

        for (String signal : KNOWLEDGE_STRONG_SIGNALS) {
            if (query.contains(signal)) {
                return new RecognitionResult(IntentType.KNOWLEDGE_QUERY, 0.99,
                        String.format("强信号词: %s", signal));
            }
        }

        return null;
    }

    /**
     * 检查特殊模式
     */
    private RecognitionResult checkSpecialPatterns(String query) {
        // 模式1: "XXX的YYY是多少" 通常是问数
        // 例如："张三的销售总额是多少"
        Pattern dataPattern1 = Pattern.compile(".+的.+是多少");
        if (dataPattern1.matcher(query).find()) {
            // 检查是否包含统计指标
            String[] statisticalIndicators = {"总额", "总和", "合计", "数量", "金额", "平均数", "平均值", "最大值", "最小值"};
            for (String indicator : statisticalIndicators) {
                if (query.contains(indicator)) {
                    return new RecognitionResult(IntentType.DATA_QUERY, 0.95,
                            String.format("特殊模式匹配: '的...是多少' + 统计指标'%s'", indicator));
                }
            }
        }

        // 模式2: "统计XXX的YYY" 显然是问数
        Pattern dataPattern2 = Pattern.compile("统计.+的.+");
        if (dataPattern2.matcher(query).find()) {
            return new RecognitionResult(IntentType.DATA_QUERY, 0.96,
                    "特殊模式匹配: '统计...的...'");
        }

        // 模式3: "查询XXX的YYY" 但YYY是统计指标 -> 问数
        Pattern dataPattern3 = Pattern.compile("查询.+的.+");
        if (dataPattern3.matcher(query).find()) {
            String[] statisticalIndicators = {"总额", "销售", "收入", "利润", "数量", "金额", "数据", "记录"};
            for (String indicator : statisticalIndicators) {
                if (query.contains(indicator)) {
                    return new RecognitionResult(IntentType.DATA_QUERY, 0.94,
                            String.format("特殊模式匹配: '查询...的...' + 统计指标'%s'", indicator));
                }
            }
        }

        return null;
    }

    /**
     * 特征匹配
     */
    private RecognitionResult matchByFeatures(String lowerQuery, String originalQuery) {
        double dataScore = 0.0;
        double knowledgeScore = 0.0;

        // 计算问数分数
        for (Map.Entry<String, Double> entry : DATA_FEATURES.entrySet()) {
            if (lowerQuery.contains(entry.getKey())) {
                dataScore += entry.getValue();
            }
        }

        // 计算问策分数
        for (Map.Entry<String, Double> entry : KNOWLEDGE_FEATURES.entrySet()) {
            if (lowerQuery.contains(entry.getKey())) {
                knowledgeScore += entry.getValue();
            }
        }

        // 特征增强
        dataScore += enhanceDataFeatures(originalQuery);
        knowledgeScore += enhanceKnowledgeFeatures(originalQuery);

        // 特殊规则：如果包含"是多少"但主要是问数特征
        if (lowerQuery.contains("是多少")) {
            // 如果包含统计词，加强问数
            if (dataScore > 0) {
                dataScore += 1.0;
            }
        }

        // 决策
        return makeDecision(dataScore, knowledgeScore, lowerQuery);
    }

    /**
     * 增强问数特征
     */
    private double enhanceDataFeatures(String query) {
        double score = 0.0;

        // 包含数字
        if (NUMERIC_PATTERN.matcher(query).find()) {
            score += 0.2;
        }

        // 包含日期
        if (DATE_PATTERN.matcher(query).find()) {
            score += 0.2;
        }

        // 包含比较词
        if (COMPARISON_PATTERN.matcher(query).find()) {
            score += 0.1;
        }

        // 包含"是多少"但前面是统计词
        if (query.contains("是多少")) {
            String before = query.substring(0, query.indexOf("是多少"));
            String[] statisticalWords = {"总额", "总和", "合计", "数量", "金额", "销售", "收入", "利润"};
            for (String word : statisticalWords) {
                if (before.contains(word)) {
                    score += 0.5;
                    break;
                }
            }
        }

        return score;
    }

    /**
     * 增强问策特征
     */
    private double enhanceKnowledgeFeatures(String query) {
        double score = 0.0;

        // 包含问号
        if (query.contains("？") || query.contains("?")) {
            score += 0.2;
        }

        // 包含"的"字结构
        if (query.contains("的")) {
            score += 0.1;
        }

        // 包含具体人名
        if (PERSON_NAME_PATTERN.matcher(query).find()) {
            score += 0.3;
        }

        // 包含车牌号
        if (LICENSE_PLATE_PATTERN.matcher(query).find()) {
            score += 0.5;
        }

        return score;
    }

    /**
     * 最终决策
     */
    private RecognitionResult makeDecision(double dataScore, double knowledgeScore, String query) {
        String reason;
        double confidence;
        IntentType intentType;

        // 特殊规则："XXX的YYY是多少"模式
        if (query.matches(".+的.+是多少")) {
            // 如果包含统计词，偏向问数
            String[] statisticalWords = {"总额", "总和", "销售", "收入", "利润", "数量", "金额"};
            boolean hasStatisticalWord = false;
            for (String word : statisticalWords) {
                if (query.contains(word)) {
                    hasStatisticalWord = true;
                    break;
                }
            }

            if (hasStatisticalWord) {
                dataScore += 2.0; // 大幅增加问数分数
            }
        }

        if (dataScore > knowledgeScore) {
            intentType = IntentType.DATA_QUERY;
            confidence = calculateConfidence(dataScore, knowledgeScore);
            reason = String.format("问数特征得分: %.2f > 问策特征得分: %.2f", dataScore, knowledgeScore);
        } else if (knowledgeScore > dataScore) {
            intentType = IntentType.KNOWLEDGE_QUERY;
            confidence = calculateConfidence(knowledgeScore, dataScore);
            reason = String.format("问策特征得分: %.2f > 问数特征得分: %.2f", knowledgeScore, dataScore);
        } else {
            intentType = IntentType.UNKNOWN;
            confidence = 0.5;
            reason = String.format("特征得分相等: %.2f", dataScore);
        }

        // 句子长度启发式调整
        confidence = adjustBySentenceLength(confidence, query);

        return new RecognitionResult(intentType, confidence, reason);
    }

    /**
     * 计算置信度
     */
    private double calculateConfidence(double winnerScore, double loserScore) {
        if (winnerScore == 0 && loserScore == 0) {
            return 0.0;
        }

        double diff = winnerScore - loserScore;
        double total = winnerScore + loserScore;
        double baseConfidence = diff / total;

        if (winnerScore >= 3.0) {
            baseConfidence = Math.min(0.99, baseConfidence + 0.3);
        } else if (winnerScore >= 2.0) {
            baseConfidence = Math.min(0.95, baseConfidence + 0.2);
        } else if (winnerScore >= 1.0) {
            baseConfidence = Math.min(0.9, baseConfidence + 0.1);
        }

        return Math.max(0.0, Math.min(1.0, baseConfidence));
    }

    /**
     * 根据句子长度调整置信度
     */
    private double adjustBySentenceLength(double confidence, String query) {
        int wordCount = query.split("\\s+").length;

        if (wordCount <= 3) {
            return confidence * 0.9;
        } else if (wordCount >= 15) {
            return Math.min(0.99, confidence * 1.1);
        }

        return confidence;
    }

    /**
     * 批量识别
     */
    public List<RecognitionResult> batchRecognize(List<String> queries) {
        return queries.stream()
                .map(this::recognize)
                .collect(Collectors.toList());
    }

    /**
     * 测试方法
     */
    public static void main(String[] args) {
        HighwayIntentRecognizer recognizer = new HighwayIntentRecognizer();

        // 测试用例
        String[] testCases = {
                "名字叫张三的雇员的销售总额是多少",  // 问数
                "张三的家庭住址是什么",           // 问策
                "统计本月各收费站通行车辆数量",   // 问数
                "如何办理ETC业务",              // 问策
                "查询李四的手机号码",           // 问策
                "分析王五的销售趋势",           // 问数
                "车牌号京A12345是什么品牌",     // 问策
                "计算各部门的平均工资"          // 问数
        };

        System.out.println("=== 修正版意图识别测试 ===\n");

        for (String query : testCases) {
            RecognitionResult result = recognizer.recognize(query);

            String intentStr = result.getIntentType() == IntentType.DATA_QUERY ?
                    "📊 问数" : (result.getIntentType() == IntentType.KNOWLEDGE_QUERY ?
                                "📚 问策" : "❓ 未知");

            System.out.printf("查询: %s\n", query);
            System.out.printf("识别: %s (置信度: %.1f%%) %s\n",
                    intentStr, result.getConfidence() * 100,
                    result.isCertain() ? "✅" : "⚠️");
            System.out.printf("依据: %s\n\n", result.getReason());
        }
    }
}
