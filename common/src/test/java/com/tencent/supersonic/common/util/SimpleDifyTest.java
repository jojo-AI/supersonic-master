package com.tencent.supersonic.common.util;

import org.junit.jupiter.api.Test;

/**
 * JOJO-Test:测试Dify连接
 */
public class SimpleDifyTest {

    public static void main(String[] args) {
        System.out.println("=== 开始Dify连接测试 ===");

        // 使用您的成功参数
        DifyClient client = new DifyClient("http://128.23.20.184:6080/v1/chat-messages",
                "Bearer app-PDLZwSqswqcas7VqmBrBJALs", "1988539680778969090", // 注意：dataset_name 改为
                                                                              // "default"
                "Bearer dataset-hvKCuzhqzojUbBuy2Kesqokp");

        // 测试连接
        DifyResult result = client.generate("你好？121是一种密码吗？是哪个文件说的，原文是怎么描述的？", "user-123");

        System.out.println("\n测试结果: " + (result.isSuccess() ? "✅ 成功" : "❌ 失败"));
        System.out.println("回答: " + result.getAnswer());
        System.out.println("原始响应: " + result);

        if (result.getErrorMessage() != null) {
            System.out.println("错误信息: " + result.getErrorMessage());
        }

        System.out.println("\n=== 测试完成 ===");
    }
}
