package com.tencent.supersonic.common.util;

import lombok.Data;

import java.util.Map;

@Data
public class DifyRequest {
    // 输入参数
    private Map<String, String> inputs;

    // 用户查询
    private String query;

    // 用户标识
    private String user;

    // 对话ID
    private String conversationId;

    // 响应模式：blocking 或 streaming
    private String responseMode = "blocking";

    // 文件列表（可选）
    private Object files;

    // 工作流ID（可选）
    private String workflowId;

    // 其他可选参数
    private Map<String, Object> additionalParams;
}
