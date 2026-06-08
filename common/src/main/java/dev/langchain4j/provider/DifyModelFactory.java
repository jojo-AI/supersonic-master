package dev.langchain4j.provider;

import com.tencent.supersonic.common.pojo.ChatModelConfig;
import com.tencent.supersonic.common.pojo.EmbeddingModelConfig;
import com.tencent.supersonic.common.util.AESEncryptionUtil;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.dify.DifyAiChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

@Service
public class DifyModelFactory implements ModelFactory, InitializingBean {
    public static final String PROVIDER = "DIFY";

    public static final String DEFAULT_BASE_URL = "http://128.23.20.184:6080/v1/chat-messages";
    public static final String DEFAULT_MODEL_NAME = "demo-DifyChat";
    public static final String DEFAULT_EMBEDDING_MODEL_NAME = "all-minilm";

    // JOJO-TEST：新增String datasetName, String datasetAuth用于私有知识库检索（小灵问策二期功能）
    public static final String DEFAULT_DATASET_NAME = "1988539680778969090";
    public static final String DEFAULT_DATASET_AUTH = "Bearer dataset-hvKCuzhqzojUbBuy2Kesqokp";

    @Override
    public ChatLanguageModel createChatModel(ChatModelConfig modelConfig) {
        return DifyAiChatModel.builder().baseUrl(modelConfig.getBaseUrl())
                .apiKey(AESEncryptionUtil.aesDecryptECB(modelConfig.getApiKey()))
                .modelName(modelConfig.getModelName()).timeOut(modelConfig.getTimeOut())
                .datasetName(getDatasetName(modelConfig)).datasetAuth(getDatasetAuth(modelConfig))
                .build();
    }

    @Override
    public OpenAiStreamingChatModel createChatStreamingModel(ChatModelConfig modelConfig) {
        throw new RuntimeException("待开发");
    }

    @Override
    public EmbeddingModel createEmbeddingModel(EmbeddingModelConfig embeddingModelConfig) {
        return OpenAiEmbeddingModel.builder().baseUrl(embeddingModelConfig.getBaseUrl())
                .apiKey(embeddingModelConfig.getApiKey())
                .modelName(embeddingModelConfig.getModelName())
                .maxRetries(embeddingModelConfig.getMaxRetries())
                .logRequests(embeddingModelConfig.getLogRequests())
                .logResponses(embeddingModelConfig.getLogResponses()).build();
    }

    @Override
    public void afterPropertiesSet() {
        ModelProvider.add(PROVIDER, this);
    }

    private String getDatasetName(ChatModelConfig modelConfig) {
        // 这里可以从modelConfig的扩展参数中获取dataset_name
        return DEFAULT_DATASET_NAME;
    }

    private String getDatasetAuth(ChatModelConfig modelConfig) {
        // 这里可以从modelConfig的扩展参数中获取dataset_auth
        return DEFAULT_DATASET_AUTH;
    }

    private String getModelName(String configModelName) {
        if (configModelName == null || configModelName.trim().isEmpty()) {
            return DEFAULT_MODEL_NAME;
        }
        return configModelName;
    }
}
