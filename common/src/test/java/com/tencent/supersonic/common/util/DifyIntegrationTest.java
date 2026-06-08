package com.tencent.supersonic.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
public class DifyIntegrationTest {

    @Test
    public void testDifyConnection() {
        // 使用您的成功参数
        DifyClient client = new DifyClient("http://128.23.20.184:6080/v1/chat-messages",
                "Bearer app-pNbpzRDBUr5yz3nJgPaSn4Nz", "default", // 注意：dataset_name 改为 "default"
                "");

        // 测试连接
        DifyResult result = client.generate("你好", "user-123");

        System.out.println("测试结果: " + result.isSuccess());
        System.out.println("回答: " + result.getAnswer());
        System.out.println("完整响应: " + result.getRawResponse());

        // 断言
        assert result.isSuccess() : "Dify连接失败";
        assert result.getAnswer() != null && !result.getAnswer().isEmpty() : "Dify返回了空答案";
    }

    @Test
    public void testWithSupersonicConfig() {
        // 模拟Supersonic的配置
        String baseUrl = "http://128.23.20.184:6080/v1/chat-messages";
        String apiKey = "Bearer app-pNbpzRDBUr5yz3nJgPaSn4Nz";
        String datasetName = "default";

        DifyClient client = new DifyClient(baseUrl, apiKey, datasetName, "");

        // 测试不同类型的问题
        String[] testQuestions = {"你好", "介绍一下你自己", "什么是高速公路ETC", "如何办理ETC"};

        for (String question : testQuestions) {
            System.out.println("\n问题: " + question);
            DifyResult result = client.generate(question, "test_user");
            System.out.println("回答: " + result.getAnswer());
            System.out.println("成功: " + result.isSuccess());

            if (!result.isSuccess()) {
                System.out.println("错误: " + result.getErrorMessage());
            }
        }
    }
}
