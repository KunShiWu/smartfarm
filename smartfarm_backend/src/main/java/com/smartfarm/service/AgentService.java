
package com.smartfarm.service;

import com.smartfarm.common.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
@Service
public class AgentService {

    private final RestTemplate restTemplate;
    private final String agentBaseUrl;

    public AgentService(
            @Value("${smartfarm.agent.base-url}") String agentBaseUrl
    ) {
        this.agentBaseUrl = agentBaseUrl.replaceAll("/+$", "");

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(5000);
        factory.setReadTimeout(300000);

        this.restTemplate = new RestTemplate(factory);
    }

    // 兼容之前只有 message 的调用
    public String chat(String message) {
        return chat(message, List.of());
    }

    // 新增：支持转发历史对话
    // 兼容原来的历史对话调用
    public String chat(
            String message,
            List<Map<String, String>> history
    ) {
        return chat(message, history, null);
    }

    // 新增：支持图片ID
    public String chat(
            String message,
            List<Map<String, String>> history,
            String imageId
    ) {

        if (message == null || message.isBlank()) {
            throw new BizException("用户问题不能为空");
        }

        if (message.length() > 2000) {
            throw new BizException("用户问题不能超过2000字符");
        }

        // 过滤不合法的历史消息
        List<Map<String, String>> safeHistory = new ArrayList<>();

        if (history != null) {
            for (Map<String, String> item : history) {
                if (item == null) {
                    continue;
                }

                String role = item.get("role");
                String content = item.get("content");

                if (!"user".equals(role)
                        && !"assistant".equals(role)) {
                    continue;
                }

                if (content == null || content.isBlank()) {
                    continue;
                }

                safeHistory.add(Map.of(
                        "role", role,
                        "content", content.substring(
                                0, Math.min(content.length(), 2000)
                        )
                ));
            }
        }

        // 只保留最近10条消息
        if (safeHistory.size() > 10) {
            safeHistory = new ArrayList<>(
                    safeHistory.subList(
                            safeHistory.size() - 10,
                            safeHistory.size()
                    )
            );
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 构建传递给 Python Agent 的请求
        Map<String, Object> body = new LinkedHashMap<>();

        body.put("message", message);
        body.put("history", safeHistory);

// 如果上传了图片，则传递image_id
        if (imageId != null && !imageId.isBlank()) {
            body.put("image_id", imageId);
        }
        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response =
                    restTemplate.exchange(
                            agentBaseUrl + "/agent/chat",
                            HttpMethod.POST,
                            request,
                            Map.class
                    );

            Map<?, ?> result = response.getBody();

            if (result == null
                    || !(result.get("answer") instanceof String)) {
                throw new BizException("Agent返回数据格式异常");
            }

            return (String) result.get("answer");

        } catch (RestClientException e) {
            throw new BizException(
                    "Python Agent 服务调用失败", e
            );
        }
    }
}
