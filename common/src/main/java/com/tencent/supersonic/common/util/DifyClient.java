package com.tencent.supersonic.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class DifyClient {
    private static final String DEFAULT_USER = "JOJO";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private String difyURL;
    private String difyKey;
    private String datasetName;
    private String datasetAuth;
    private final RestTemplate restTemplate = new RestTemplate();

    public DifyClient(String difyURL, String difyKey, String datasetName, String datasetAuth) {
        this.difyURL = difyURL;
        this.difyKey = difyKey;
        this.datasetName = datasetName;
        this.datasetAuth = datasetAuth;
    }

    public DifyClient(String baseUrl, String apiKey) {
        this.difyURL = baseUrl;
        this.difyKey = apiKey;
        this.datasetName = null;
        this.datasetAuth = null;
    }

    public DifyResult generate(String prompt) {
        return generate(prompt, DEFAULT_USER);
    }

    public DifyResult generate(String prompt, String user) {
        Map<String, String> inputs = new HashMap<>();
        return generate(inputs, prompt, user, null);
    }

    public DifyResult generate(Map<String, String> inputs, String queryText, String user,
            String conversationId) {
        Map<String, String> headers = defaultHeaders();
        DifyRequest request = new DifyRequest();

        // 构建inputs，确保包含必要的认证参数
        Map<String, String> finalInputs = buildInputsWithAuth(inputs);

        request.setInputs(finalInputs);
        request.setQuery(queryText);
        request.setUser(user);
        if (conversationId != null && !conversationId.isEmpty()) {
            request.setConversationId(conversationId);
        }

        // 设置响应模式为blocking（同步）
        request.setResponseMode("blocking");

        return sendRequest(request, headers);
    }

    /**
     * 构建包含认证信息的inputs
     */
    private Map<String, String> buildInputsWithAuth(Map<String, String> originalInputs) {
        Map<String, String> inputs = new HashMap<>();

        // 从difyKey中提取纯API Key（dify要求的去掉Bearer前缀）
        String pureApiKey = extractPureApiKey(difyKey);
        // 添加必要的认证参数
        if (difyKey != null && !difyKey.isEmpty()) {
            // 关键：在inputs中添加authorization字段
            inputs.put("Authorization", pureApiKey);
        }

        if (datasetName != null && !datasetName.isEmpty()) {
            inputs.put("dataset_name", datasetName != null ? datasetName : "default");
        }

        if (datasetAuth != null && !datasetAuth.isEmpty()) {
            inputs.put("dataset_auth", datasetAuth);
        }

        // 添加原始inputs
        if (originalInputs != null) {
            inputs.putAll(originalInputs);
        }

        return inputs;
    }


    /**
     * 从"Bearer app-xxx"中提取纯API Key
     */
    private String extractPureApiKey(String fullKey) {
        if (fullKey == null || fullKey.trim().isEmpty()) {
            return "";
        }
        String trimmed = fullKey.trim();
        if (trimmed.startsWith("Bearer ")) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }

    /**
     * 修改后的sendRequest方法，使用RestTemplate直接调用
     */
    public DifyResult sendRequest(DifyRequest request, Map<String, String> headers) {
        try {
            log.debug("请求dify- URL: {}", difyURL);
            log.debug("请求dify- header: {}", JsonUtil.toString(headers));
            log.debug("请求dify- request: {}", JsonUtil.toString(request));

            // 构建HttpHeaders
            HttpHeaders httpHeaders = new HttpHeaders();
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                httpHeaders.add(entry.getKey(), entry.getValue());
            }

            // 构建请求体
            String requestBody = JsonUtil.toString(request);

            // 创建HttpEntity
            HttpEntity<String> entity = new HttpEntity<>(requestBody, httpHeaders);

            // 发送请求
            ResponseEntity<String> response =
                    restTemplate.exchange(difyURL, HttpMethod.POST, entity, String.class);

            String responseBody = response.getBody();
            log.debug("dify响应: {}", responseBody);

            // 解析响应
            return parseResponse(responseBody, response.getStatusCode());

        } catch (HttpClientErrorException e) {
            log.error("HTTP客户端错误: {}, 响应: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            return DifyResult.builder()
                    .answer("HTTP客户端错误: " + e.getStatusCode() + " - " + e.getResponseBodyAsString())
                    .success(false).build();
        } catch (HttpServerErrorException e) {
            log.error("HTTP服务器错误: {}, 响应: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            return DifyResult.builder()
                    .answer("HTTP服务器错误: " + e.getStatusCode() + " - " + e.getResponseBodyAsString())
                    .success(false).build();
        } catch (ResourceAccessException e) {
            log.error("网络连接错误: {}", e.getMessage(), e);
            return DifyResult.builder().answer("网络连接错误: " + e.getMessage()).success(false).build();
        } catch (RestClientException e) {
            log.error("HTTP请求异常: {}", e.getMessage(), e);
            return DifyResult.builder().answer("HTTP请求异常: " + e.getMessage()).success(false)
                    .build();
        } catch (Exception e) {
            log.error("请求dify失败: {}", e.getMessage(), e);
            return DifyResult.builder().answer("请求dify失败: " + e.getMessage()).success(false)
                    .build();
        }
    }

    /**
     * 解析Dify响应
     */
    private DifyResult parseResponse(String responseBody, HttpStatusCode statusCode) {
        try {
            if (responseBody == null || responseBody.isEmpty()) {
                return DifyResult.builder().answer("Dify返回了空响应").success(false).build();
            }

            // 解析JSON响应
            JsonNode rootNode = objectMapper.readTree(responseBody);

            // 检查是否包含错误
            if (rootNode.has("code") && !"success".equals(rootNode.get("code").asText())) {
                String errorMessage =
                        rootNode.has("message") ? rootNode.get("message").asText() : "未知错误";
                return DifyResult.builder().answer("Dify错误: " + errorMessage).success(false)
                        .build();
            }

            // 提取answer
            String answer = "";
            if (rootNode.has("data") && rootNode.get("data").has("answer")) {
                answer = rootNode.get("data").get("answer").asText();
            } else if (rootNode.has("answer")) {
                answer = rootNode.get("answer").asText();
            } else {
                // 如果找不到answer，尝试从其他字段获取
                answer = responseBody;
            }

            return DifyResult.builder().answer(answer).success(true).build();

        } catch (Exception e) {
            log.error("解析Dify响应失败: {}, 原始响应: {}", e.getMessage(), responseBody, e);
            return DifyResult.builder().answer("解析响应失败: " + e.getMessage()).success(false).build();
        }
    }

    public String parseSQLResult(String sql) {
        Pattern pattern = Pattern.compile("```(sql)?(.*)```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(sql);
        if (!matcher.find()) {
            return sql.trim();
        } else {
            return matcher.group(2).trim();
        }
    }

    private Map<String, String> defaultHeaders() {
        Map<String, String> headers = new HashMap<>();
        String authHeaderValue = difyKey.contains("Bearer") ? difyKey : "Bearer " + difyKey;
        headers.put("Authorization", authHeaderValue);
        headers.put("Content-Type", CONTENT_TYPE_JSON);

        // 根据之前的测试，可能还需要在header中添加dataset_name
        if (datasetName != null && !datasetName.isEmpty()) {
            headers.put("dataset_name", datasetName);
        }

        return headers;
    }

    /**
     * 测试连接方法
     */
    public DifyResult testConnection() {
        try {
            // 发送一个简单的测试请求
            Map<String, String> testHeaders = defaultHeaders();

            DifyRequest testRequest = new DifyRequest();
            Map<String, String> testInputs = new HashMap<>();
            testInputs.put("authorization", "Bearer " + difyKey);
            if (datasetName != null) {
                testInputs.put("dataset_name", datasetName);
            }
            if (datasetAuth != null) {
                testInputs.put("dataset_auth", datasetAuth);
            }

            testRequest.setInputs(testInputs);
            testRequest.setQuery("test");
            testRequest.setUser("test_user");
            testRequest.setResponseMode("blocking");

            return sendRequest(testRequest, testHeaders);

        } catch (Exception e) {
            return DifyResult.builder().answer("测试连接失败: " + e.getMessage()).success(false).build();
        }
    }
}
