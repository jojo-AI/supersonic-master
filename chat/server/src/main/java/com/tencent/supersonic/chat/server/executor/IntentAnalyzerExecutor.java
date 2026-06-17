package com.tencent.supersonic.chat.server.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tencent.supersonic.chat.api.pojo.response.IntentResult;
import com.tencent.supersonic.chat.api.pojo.response.QueryResp;
import com.tencent.supersonic.chat.server.agent.Agent;
import com.tencent.supersonic.chat.server.service.AgentService;
import com.tencent.supersonic.chat.server.service.ChatManageService;
import com.tencent.supersonic.common.pojo.ChatApp;
import com.tencent.supersonic.common.pojo.enums.AppModule;
import com.tencent.supersonic.common.util.ChatAppManager;
import com.tencent.supersonic.common.util.ContextUtils;
import com.tencent.supersonic.headless.api.pojo.response.QueryState;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.provider.ModelProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class IntentAnalyzerExecutor {

    public static final String APP_KEY = "SMALL_TALK";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String INTENT_TYPE_DATA = "问数";

    private static final String INTENT_TYPE_POLICY = "问策";

    private static final String INSTRUCTION =
            "#Role:\n"
                    + "You are a precise intent parser for a dual-function assistant.\n"
                    + "\n"
                    + "#Task:\n"
                    + "Based on the conversation history and current user input, decompose the user's needs "
                    + "into a list of self-contained executable tasks. Each task belongs to exactly one of two types:\n"
                    + "- \"问数\": query structured databases for real-time facts, statistics, metrics, counts, sums, rankings, records, or specific field values.\n"
                    + "- \"问策\": search unstructured knowledge bases for policies, rules, standards, guidelines, explanations, judgments, or suggestions.\n"
                    + "\n"
                    + "Rewrite each task's query as \"intentQuery\" so that it is fully explicit and can be executed independently. "
                    + "You must incorporate necessary context from conversation history, such as entities, pronouns, time ranges, numbers, conditions, and omitted references.\n"
                    + "\n"
                    + "#Rules:\n"
                    + "1. Always consider the entire conversation history to resolve pronouns, omitted references, and ambiguous terms.\n"
                    + "2. If the current user input contains both data-oriented and policy-oriented needs, split them into separate tasks.\n"
                    + "3. For \"问数\" intents, intentQuery must clearly state what to query, count, sum, retrieve, compare, filter, or aggregate.\n"
                    + "4. For \"问策\" intents, intentQuery must clearly state the policy, rule, standard, explanation, judgment, or suggestion to search or infer from the knowledge base.\n"
                    + "5. If the current input depends on previous turns, rewrite intentQuery with the resolved concrete subject instead of vague words like \"这个\", \"他\", \"它\", \"刚才那个\".\n"
                    + "6. If there is no actionable intent, output an empty JSON array: [].\n"
                    + "\n"
                    + "#Strict Output Requirements:\n"
                    + "You must output ONLY a valid JSON array.\n"
                    + "Do not output markdown.\n"
                    + "Do not output ```json.\n"
                    + "Do not output explanations.\n"
                    + "Do not output any text before or after the JSON array.\n"
                    + "Do not output comments.\n"
                    + "Do not wrap the JSON array in an object.\n"
                    + "The JSON array item schema must be exactly:\n"
                    + "{\n"
                    + "  \"intentType\": \"问数\" or \"问策\",\n"
                    + "  \"intentQuery\": \"rewritten executable query\"\n"
                    + "}\n"
                    + "\n"
                    + "#Valid Output Example:\n"
                    + "[{\"intentType\":\"问数\",\"intentQuery\":\"查询今天车牌为粤A555的车辆载重数值\"}]\n"
                    + "\n"
                    + "#Invalid Output Examples:\n"
                    + "```json\n"
                    + "[{\"intentType\":\"问数\",\"intentQuery\":\"查询今天车牌为粤A555的车辆载重数值\"}]\n"
                    + "```\n"
                    + "Here is the result: [{\"intentType\":\"问数\",\"intentQuery\":\"查询今天车牌为粤A555的车辆载重数值\"}]\n"
                    + "{\"results\":[{\"intentType\":\"问数\",\"intentQuery\":\"查询今天车牌为粤A555的车辆载重数值\"}]}\n"
                    + "\n"
                    + "#Examples:\n"
                    + "Example 1:\n"
                    + "History: []\n"
                    + "Current: 今天粤A555车的载重多少？\n"
                    + "Output: [{\"intentType\":\"问数\",\"intentQuery\":\"查询今天车牌为粤A555的车辆载重数值\"}]\n"
                    + "\n"
                    + "Example 2:\n"
                    + "History: 今天粤A555车的载重多少？\n"
                    + "Current: 这个载重是否超过标准？\n"
                    + "Output: [{\"intentType\":\"问策\",\"intentQuery\":\"根据知识库查询车牌粤A555所属车型的载重标准限值，并判断当前载重是否超限\"}]\n"
                    + "\n"
                    + "Example 3:\n"
                    + "History: []\n"
                    + "Current: 我们部门有多少人？有没有超过编制？\n"
                    + "Output: [{\"intentType\":\"问数\",\"intentQuery\":\"统计当前用户所属部门的在职员工人数\"},{\"intentType\":\"问策\",\"intentQuery\":\"查询知识库中关于当前用户所属部门正式编制人数及是否超编的规定\"}]\n"
                    + "\n"
                    + "Example 4:\n"
                    + "History: 帮我查一下张三上个月的销售总额\n"
                    + "Current: 那他这个月能不能完成KPI？\n"
                    + "Output: [{\"intentType\":\"问数\",\"intentQuery\":\"查询张三本月当前销售总额以及张三本月KPI目标值\"},{\"intentType\":\"问策\",\"intentQuery\":\"查询知识库中关于销售KPI完成标准的定义和判断规则\"}]\n"
                    + "\n"
                    + "#History Inputs:\n"
                    + "%s\n"
                    + "\n"
                    + "#Current Input:\n"
                    + "%s\n"
                    + "\n"
                    + "#Response:\n";

    public IntentAnalyzerExecutor() {
        ChatAppManager.register(APP_KEY, ChatApp.builder().prompt(INSTRUCTION).name("意图解析器")
                .appModule(AppModule.CHAT).description("将用户query输入大模型进行意图解析").enable(false).build());
    }

    public List<IntentResult> execute(Integer agentId, String queryText, Integer chatId) {
        AgentService agentService = ContextUtils.getBean(AgentService.class);
        Agent chatAgent = agentService.getAgent(agentId);
        ChatApp chatApp = chatAgent.getChatAppConfig().get(APP_KEY);
        if (Objects.isNull(chatApp) || !chatApp.isEnable()) {
            return null;
        }

        String p = INSTRUCTION;
        // 当前问题由：提示词+上下文历史输入+用户输入组成
        String promptStr = String.format(p, getHistoryInputs(chatId), queryText);

        Prompt prompt = PromptTemplate.from(promptStr).apply(Collections.emptyMap());

        ChatLanguageModel chatLanguageModel =
                ModelProvider.getChatModel(chatApp.getChatModelConfig());

        Response<AiMessage> response = chatLanguageModel.generate(prompt.toUserMessage());

        String responseText = null;
        if (response != null && response.content() != null) {
            responseText = response.content().text();
        }

        // 根据拆分出来的 queryIntent 拼接成 List<IntentResult>
        List<IntentResult> results = parseIntentResults(responseText);
        sortIntentResults(results);
        return results;
    }

    private void sortIntentResults(List<IntentResult> results) {
        if (results == null || results.size() <= 1) {
            return;
        }

        results.sort((left, right) -> {
            int leftOrder = getIntentTypeOrder(left);
            int rightOrder = getIntentTypeOrder(right);
            return Integer.compare(leftOrder, rightOrder);
        });
    }

    private int getIntentTypeOrder(IntentResult intentResult) {
        if (intentResult == null || intentResult.getIntentType() == null) {
            return Integer.MAX_VALUE;
        }
        if (INTENT_TYPE_DATA.equals(intentResult.getIntentType().trim())) {
            return 0;
        }
        if (INTENT_TYPE_POLICY.equals(intentResult.getIntentType().trim())) {
            return 1;
        }
        return Integer.MAX_VALUE;
    }

    private List<IntentResult> parseIntentResults(String responseText) {
        if (responseText == null || responseText.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String cleanedText = cleanAiResponse(responseText);

        List<IntentResult> directResults = tryParseIntentResults(cleanedText);
        if (!directResults.isEmpty()) {
            return directResults;
        }

        String jsonArrayText = extractBalancedJson(cleanedText, '[', ']');
        if (jsonArrayText != null) {
            List<IntentResult> arrayResults = tryParseIntentResults(jsonArrayText);
            if (!arrayResults.isEmpty()) {
                return arrayResults;
            }
        }

        String jsonObjectText = extractBalancedJson(cleanedText, '{', '}');
        if (jsonObjectText != null) {
            List<IntentResult> objectResults = tryParseIntentResults(jsonObjectText);
            if (!objectResults.isEmpty()) {
                return objectResults;
            }
        }

        return Collections.emptyList();
    }

    private List<IntentResult> tryParseIntentResults(String jsonText) {
        if (jsonText == null || jsonText.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(jsonText);

            if (rootNode == null || rootNode.isNull()) {
                return Collections.emptyList();
            }

            if (rootNode.isArray()) {
                return parseIntentResultArray(rootNode);
            }

            if (rootNode.isObject()) {
                JsonNode arrayNode = findFirstArrayNode(rootNode, "results", "data", "result", "intentResults");
                if (arrayNode != null) {
                    return parseIntentResultArray(arrayNode);
                }

                IntentResult singleResult = parseIntentResultObject(rootNode);
                if (singleResult != null) {
                    return Collections.singletonList(singleResult);
                }
            }

            return Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<IntentResult> parseIntentResultArray(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return Collections.emptyList();
        }

        List<IntentResult> results = new ArrayList<>();
        for (JsonNode itemNode : arrayNode) {
            IntentResult intentResult = parseIntentResultObject(itemNode);
            if (intentResult != null) {
                results.add(intentResult);
            }
        }

        return results;
    }

    private IntentResult parseIntentResultObject(JsonNode objectNode) {
        if (objectNode == null || !objectNode.isObject()) {
            return null;
        }

        String intentType = getTextValue(objectNode, "intentType");
        String intentQuery = getTextValue(objectNode, "intentQuery");

        if (!isValidIntentType(intentType)) {
            return null;
        }

        if (intentQuery == null || intentQuery.trim().isEmpty()) {
            return null;
        }

        IntentResult intentResult = new IntentResult();
        intentResult.setIntentType(intentType.trim());
        intentResult.setIntentQuery(intentQuery.trim());
        return intentResult;
    }

    private boolean isValidIntentType(String intentType) {
        if (intentType == null) {
            return false;
        }

        String trimmedIntentType = intentType.trim();
        return INTENT_TYPE_DATA.equals(trimmedIntentType) || INTENT_TYPE_POLICY.equals(trimmedIntentType);
    }

    private String getTextValue(JsonNode node, String fieldName) {
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }

        if (valueNode.isTextual()) {
            return valueNode.asText();
        }

        return valueNode.toString();
    }

    private JsonNode findFirstArrayNode(JsonNode rootNode, String... fieldNames) {
        if (rootNode == null || !rootNode.isObject()) {
            return null;
        }

        for (String fieldName : fieldNames) {
            JsonNode node = rootNode.get(fieldName);
            if (node != null && node.isArray()) {
                return node;
            }
        }

        return null;
    }

    private String cleanAiResponse(String responseText) {
        String text = responseText.trim();

        text = text.replaceFirst("(?i)^\\s*```json\\s*", "");
        text = text.replaceFirst("(?i)^\\s*```\\s*", "");
        text = text.replaceFirst("\\s*```\\s*$", "");

        return text.trim();
    }

    private String extractBalancedJson(String text, char openChar, char closeChar) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        int startIndex = text.indexOf(openChar);
        if (startIndex < 0) {
            return null;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = startIndex; i < text.length(); i++) {
            char currentChar = text.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (currentChar == '\\') {
                escaped = true;
                continue;
            }

            if (currentChar == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (currentChar == openChar) {
                depth++;
            } else if (currentChar == closeChar) {
                depth--;
                if (depth == 0) {
                    return text.substring(startIndex, i + 1);
                }
            }
        }

        return null;
    }

    private String getHistoryInputs(Integer chatId) {
        StringBuilder historyInput = new StringBuilder();
        // 规定N条历史记录
        List<QueryResp> queryResps = getHistoryQueries(chatId, 3);
        queryResps.forEach(p -> {
            historyInput.append(p.getQueryText());
            historyInput.append(";");
        });

        return historyInput.toString();
    }

    private List<QueryResp> getHistoryQueries(int chatId, int multiNum) {
        ChatManageService chatManageService = ContextUtils.getBean(ChatManageService.class);
        List<QueryResp> contextualParseInfoList = chatManageService.getChatQueries(chatId).stream()
                .filter(q -> Objects.nonNull(q.getQueryResult())
                        && q.getQueryResult().getQueryState() == QueryState.SUCCESS)
                .collect(Collectors.toList());
        List<QueryResp> contextualList = contextualParseInfoList.subList(0,
                Math.min(multiNum, contextualParseInfoList.size()));
        Collections.reverse(contextualList);
        return contextualList;
    }
}