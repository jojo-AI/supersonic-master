package dev.langchain4j.model.dify;

import com.tencent.supersonic.common.util.AESEncryptionUtil;
import com.tencent.supersonic.common.util.DifyClient;
import com.tencent.supersonic.common.util.DifyResult;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static java.util.Collections.singletonList;

public class DifyAiChatModel implements ChatLanguageModel {

    private static final String CONTENT_TYPE_JSON = "application/json";

    private final String baseUrl;
    private final String apiKey;
    // 添加getter方法
    @Getter
    private final String datasetName;
    private final String datasetAuth;

    private final DifyClient difyClient;
    private final Integer maxRetries;
    private final Integer maxToken;

    private final String appName;
    private final Double temperature;
    private final Long timeOut;

    @Setter
    private String userName;

    // JOJO-TEST：新增String datasetName, String datasetAuth用于私有知识库检索（小灵问策二期功能）
    @Builder
    public DifyAiChatModel(String baseUrl, String apiKey, String datasetName, String datasetAuth,
            Integer maxRetries, Integer maxToken, String modelName, Double temperature,
            Long timeOut) {
        this.baseUrl = baseUrl;
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.maxToken = getOrDefault(maxToken, 512);

        try {
            this.apiKey = AESEncryptionUtil.aesDecryptECB(apiKey);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        this.datasetName = datasetName;
        this.datasetAuth = datasetAuth;
        this.appName = modelName;
        this.temperature = temperature;
        this.timeOut = timeOut;

        // 修改DifyClient初始化，传递额外参数
        this.difyClient =
                new DifyClient(this.baseUrl, this.apiKey, this.datasetName, this.datasetAuth);
    }

    @Override
    public String generate(String message) {
        DifyResult difyResult = this.difyClient.generate(message, this.getUserName());
        return difyResult.getAnswer();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return generate(messages, (ToolSpecification) null);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications) {
        ensureNotEmpty(messages, "messages");

        // 获取最后一条用户消息
        String lastUserMessage = "";
        for (ChatMessage msg : messages) {
            if (msg instanceof dev.langchain4j.data.message.UserMessage) {
                lastUserMessage = msg.text();
            }
        }

        if (lastUserMessage.isEmpty() && !messages.isEmpty()) {
            lastUserMessage = messages.get(messages.size() - 1).text();
        }

        DifyResult difyResult = this.difyClient.generate(lastUserMessage, this.getUserName());
        System.out.println("Dify响应: " + difyResult.toString());

        if (!isNullOrEmpty(toolSpecifications)) {
            // TODO JOJO-TEST:工具调用，这是agent智能体的后续逻辑，这里暂时不处理
        }

        return Response.from(AiMessage.from(difyResult.getAnswer()));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages,
            ToolSpecification toolSpecification) {
        return generate(messages,
                toolSpecification != null ? singletonList(toolSpecification) : null);
    }

    public String getUserName() {
        return null == userName ? "admin" : userName;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public String getDatasetAuth() {
        return datasetAuth;
    }

}
