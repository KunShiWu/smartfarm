
package com.smartfarm.controller;

import com.smartfarm.common.BizException;
import com.smartfarm.service.AgentService;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    // 接收前端 message + history
    public record ChatRequest(
            String message,
            List<Map<String, String>> history,
            @JsonProperty("image_id") String imageId
    ) {}

    @PostMapping("/chat")
    public Map<String, String> chat(
            @RequestBody ChatRequest request
    ) {

        String message = request.message();

        if (message == null || message.isBlank()) {
            throw new BizException("message不能为空");
        }

        String answer = agentService.chat(
                message,
                request.history(),
                request.imageId()
        );

        return Map.of("answer", answer);
    }
}
