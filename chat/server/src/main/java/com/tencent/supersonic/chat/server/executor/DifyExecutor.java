package com.tencent.supersonic.chat.server.executor;

import com.tencent.supersonic.common.pojo.User;
import com.tencent.supersonic.common.util.DifyClient;
import com.tencent.supersonic.common.util.DifyResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DifyExecutor {
    public String generate(User user, String promptStr) {
        log.info("JOJO Test: Dify request begin: {}, {}", user, promptStr);
        // 使用您的成功参数Bearer app-pNbpzRDBUr5yz3nJgPaSn4Nz;
        // 使用您的成功参数Bearer app-PDLZwSqswqcas7VqmBrBJALs;
        // 注意：根据青伟那边的知识库上传逻辑，知识库ID=租户ID，Dify流的Key和知识库的Key是不一样的
        DifyClient client = new DifyClient("http://128.23.20.184:6080/v1/chat-messages",
                user.getApiKey(), user.getTenantId(),
                user.getDatasetKey());

        // 通过客户端进行请求，这里上下文管理和会话管理不给Dify做，如果需要历史会话管理，可以调用另外一个接口实现
        DifyResult result = client.generate(promptStr, user.getName());

        System.out.println("\n测试结果: " + (result.isSuccess() ? "✅ 成功" : "❌ 失败"));
        System.out.println("回答: " + result.getAnswer());
        log.info("JOJO Test: Dify test Success. result: {}", result);

        if (result.getErrorMessage() != null) {
            System.out.println("错误信息: " + result.getErrorMessage());
            log.info("JOJO Test: Dify test Error, result: {}", result.getErrorMessage());
        }
        return result.getAnswer();
    }
}
