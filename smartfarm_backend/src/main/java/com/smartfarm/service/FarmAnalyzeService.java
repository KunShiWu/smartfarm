package com.smartfarm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfarm.common.BizException;
import com.smartfarm.config.AppProperties;
import com.smartfarm.model.FarmAnalyzeRequest;
import com.smartfarm.model.FarmAnalyzeResult;
import com.smartfarm.model.ParamItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
/**
 * 农田分析：调用 DeepSeek 大模型，结合摄像头截图与土壤数据做农情诊断。
 *
 * <p>两段式：先用支持图片输入的视觉模型（deepseek-flash）读取截图，产出客观观察；
 * 再用用户指定的 deepseek-v4-pro 结合观察与环境数据，给出农民下一步农事建议。
 * 二者均可通过配置替换。</p>
 */
@Service
public class FarmAnalyzeService {

    private static final Logger log = LoggerFactory.getLogger(FarmAnalyzeService.class);
    private final FarmImageService farmImageService;
    /** 截图 base64 长度上限（约 9MB 原始图），超限拒绝，避免请求体过大 */
    private static final int MAX_IMAGE_BASE64_LEN = 12_000_000;

    private static final String VISION_PROMPT =
            "你是资深农业植保专家。请仔细观察这张农田/作物监控画面截图，客观、如实地描述：\n"
            + "1) 画面中作物或植被的可见情况（作物种类、长势、株高、叶片颜色、种植密度、是否倒伏/黄化/枯萎）；\n"
            + "2) 是否存在可见的虫害迹象（虫体、咬孔、虫粪、卷叶、蛛网、虫卵等）或病害迹象（斑点、霉层、菌核、腐烂等）；\n"
            + "3) 田间的其他情况（杂草、积水、地表干裂、设施遮挡、光照等）。\n"
            + "要求：只描述画面中真实可见的内容，看不清就明确说明看不清，不要凭空推测或编造。分点输出，简洁明确。";
    private static final String DISEASE_VISION_PROMPT =
            "你是一名农业植保图像观察员，负责从农作物照片中"
                    + "提取可见症状，为后续农业诊断智能体提供证据。\n"
                    + "请遵守以下规则：\n"
                    + "1. 只依据图片中可见的信息进行分析。\n"
                    + "2. 无法确定作物名称时，填写'未确定'。\n"
                    + "3. 描述病斑颜色、形状、分布、黄化、卷曲和坏死等症状。\n"
                    + "4. 可以列出候选病害及非生物性损伤原因，但不能直接确诊。\n"
                    + "5. 不得编造病原体、传播速度、环境数据或发病范围。\n"
                    + "6. 不提供农药名称、剂量、喷药或灌溉指令。\n"
                    + "7. 不得输出Markdown、代码围栏或其他解释，只返回JSON对象。\n"
                    + "JSON必须包含以下字段：\n"
                    + "crop：字符串，作物名称；\n"
                    + "visibleSymptoms：字符串数组，可见异常症状；\n"
                    + "possibleCauses：字符串数组，可能病因；\n"
                    + "missingInformation：字符串数组，需要补充的信息；\n"
                    + "uncertainty：字符串，诊断的不确定性说明。";
    private static final String REASON_SYSTEM_PROMPT =
            "你是一位经验丰富的农业技术顾问，服务对象是普通农户。"
            + "请依据提供的【农田监控画面分析】【土壤/环境监测数据】以及农户的问题，"
            + "判断当前农田状况，并告诉农户下一步该怎么做。\n"
            + "输出要求：\n"
            + "1. 用中文，分点作答，通俗易懂，少堆术语；\n"
            + "2. 依次给出三个部分：【作物长势评估】【病虫害风险提示】【下一步农事建议】"
            + "（浇水、施肥、打药、通风、巡查等，尽量具体、可落地，并给出优先级）；\n"
            + "3. 若信息不足，明确说明还缺少哪些信息，不要编造数据；\n"
            + "4. 若画面与数据均未发现异常，也要如实说明。";

    private final AppProperties props;
    private final RestTemplate restTemplate;

    public FarmAnalyzeService(FarmImageService farmImageService, AppProperties props) {
        this.farmImageService = farmImageService;
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(props.getDeepSeek().getTimeoutSeconds() * 1000);
        this.restTemplate = new RestTemplate(factory);
    }

    /** 执行一次农田分析 */
    public FarmAnalyzeResult analyze(FarmAnalyzeRequest req) {
        AppProperties.DeepSeek cfg = props.getDeepSeek();
        if (cfg.getApiKey() == null || cfg.getApiKey().trim().isEmpty()) {
            throw new BizException("未配置 DeepSeek API Key");
        }
        long start = System.currentTimeMillis();

        String image = normalizeImage(req.imageBase64);
        String observation = null;
        if (image != null) {
            observation = callModel(cfg.getVisionModel(), visionMessages(image));
        }
        String answer = callModel(cfg.getReasonModel(), reasonMessages(req, observation));

        FarmAnalyzeResult result = new FarmAnalyzeResult();
        result.answer = answer;
        result.observation = observation;
        result.visionModel = image != null ? cfg.getVisionModel() : null;
        result.reasonModel = cfg.getReasonModel();
        result.usedImage = image != null;
        result.elapsedMs = System.currentTimeMillis() - start;
        log.info("农田分析完成, 使用截图={}, 耗时={}ms", result.usedImage, result.elapsedMs);
        return result;
    }

    /**
     * 根据 image_id 执行农作物病害视觉观察
     */
    public Map<String, Object> observeById(String imageId) {

        if (imageId == null || imageId.isBlank()) {
            throw new BizException("image_id不能为空");
        }

        // 从临时存储中读取图片
        String imageDataUrl = farmImageService.getImageDataUrl(imageId);

        // 构建现有的视觉观察请求
        FarmAnalyzeRequest request = new FarmAnalyzeRequest();

        request.imageBase64 = imageDataUrl;

        // 复用已经验证成功的视觉观察逻辑
        return observe(request);
    }

    /**
     * 农作物病害视觉观察。
     * 只调用视觉模型，不生成农事处理方案。
     */
    public Map<String, Object> observe(FarmAnalyzeRequest req) {

        if (req == null) {
            throw new BizException("请求不能为空");
        }

        AppProperties.DeepSeek cfg = props.getDeepSeek();

        if (cfg.getApiKey() == null
                || cfg.getApiKey().isBlank()) {
            throw new BizException("未配置 DeepSeek API Key");
        }

        String image = normalizeImage(req.imageBase64);

        if (image == null) {
            throw new BizException("请提供农作物图片");
        }

        long start = System.currentTimeMillis();

        // 只执行视觉模型
        String raw = callModel(
                cfg.getVisionModel(),
                visionMessages(image, DISEASE_VISION_PROMPT)
        );

        // 去除模型可能返回的 Markdown 代码围栏
        String jsonText = raw.trim();

        if (jsonText.startsWith("```")) {
            int firstNewline = jsonText.indexOf('\n');
            int lastFence = jsonText.lastIndexOf("```");

            if (firstNewline >= 0 && lastFence > firstNewline) {
                jsonText = jsonText.substring(
                        firstNewline + 1,
                        lastFence
                ).trim();
            }
        }

        JsonNode root;

        try {
            root = new ObjectMapper().readTree(jsonText);
        } catch (JsonProcessingException e) {
            throw new BizException("视觉模型未返回有效JSON", e);
        }

        if (root == null || !root.isObject()) {
            throw new BizException("病害识别结果格式异常");
        }

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("crop", root.path("crop").asText("未确定"));

        result.put(
                "visibleSymptoms",
                readStringArray(root, "visibleSymptoms")
        );

        result.put(
                "possibleCauses",
                readStringArray(root, "possibleCauses")
        );

        result.put("diagnosisConfirmed", false);

        result.put(
                "missingInformation",
                readStringArray(root, "missingInformation")
        );

        result.put(
                "uncertainty",
                root.path("uncertainty").asText(
                        "单张图像不足以确定病原，需要进一步核实"
                )
        );

        result.put("usedImage", true);
        result.put("visionModel", cfg.getVisionModel());
        result.put(
                "elapsedMs",
                System.currentTimeMillis() - start
        );

        return result;
    }


    /** 安全提取模型返回的字符串数组 */
    private static List<String> readStringArray(
            JsonNode root,
            String field
    ) {
        List<String> values = new ArrayList<>();

        JsonNode node = root.path(field);

        if (!node.isArray()) {
            return values;
        }

        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText().trim());
            }

            if (values.size() >= 8) {
                break;
            }
        }

        return values;
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    /** 校验并规整图片为 data URL；无图或非法返回 null */
    private static String normalizeImage(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String s = raw.trim();
        if (s.length() > MAX_IMAGE_BASE64_LEN) {
            throw new BizException("截图过大,请降低分辨率后重试");
        }
        if (s.startsWith("data:")) {
            return s;
        }
        return "data:image/jpeg;base64," + s;
    }

    private List<Map<String, Object>> visionMessages(String dataUrl) {
        return visionMessages(dataUrl, VISION_PROMPT);
    }

    private List<Map<String, Object>> visionMessages(
            String dataUrl,
            String prompt
    ) {
        List<Object> content = new ArrayList<>();
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("type", "text");
        text.put("text", prompt);;
        content.add(text);

        Map<String, Object> urlObj = new LinkedHashMap<>();
        urlObj.put("url", dataUrl);
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("type", "image_url");
        image.put("image_url", urlObj);
        content.add(image);

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", content);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(msg);
        return messages;
    }

    private List<Map<String, Object>> reasonMessages(FarmAnalyzeRequest req, String observation) {
        StringBuilder sb = new StringBuilder();
        String question = req.question != null && !req.question.trim().isEmpty()
                ? req.question.trim()
                : "请判断当前农田状况，并告诉我下一步该怎么做。";
        sb.append("【农户的问题】").append(question).append('\n');
        if (req.deviceName != null && !req.deviceName.isEmpty()) {
            sb.append("【监控点位】").append(req.deviceName).append('\n');
        }
        if (req.captureTime != null && !req.captureTime.isEmpty()) {
            sb.append("【截图时间】").append(req.captureTime).append('\n');
        }
        sb.append("\n【环境/土壤监测数据】\n").append(formatParams(req.params));
        sb.append("\n\n【农田监控画面分析】\n").append(
                observation != null && !observation.isEmpty()
                        ? observation
                        : "（本次未获取到画面截图，请仅依据环境数据判断，并说明缺少画面信息）");

        Map<String, Object> sys = new LinkedHashMap<>();
        sys.put("role", "system");
        sys.put("content", REASON_SYSTEM_PROMPT);
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("role", "user");
        user.put("content", sb.toString());

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(sys);
        messages.add(user);
        return messages;
    }

    private static String formatParams(List<ParamItem> params) {
        if (params == null || params.isEmpty()) {
            return "（无）";
        }
        StringBuilder sb = new StringBuilder();
        for (ParamItem p : params) {
            if (p == null || p.name == null) {
                continue;
            }
            sb.append("- ").append(p.name).append("：");
            sb.append(p.value == null ? "—" : String.valueOf(p.value));
            if (p.unit != null && !p.unit.isEmpty()) {
                sb.append(p.unit);
            }
            sb.append('\n');
        }
        return sb.length() == 0 ? "（无）" : sb.toString().trim();
    }

    private String callModel(String model, List<Map<String, Object>> messages) {
        AppProperties.DeepSeek cfg = props.getDeepSeek();
        String url = trimTrailingSlash(cfg.getBaseUrl()) + "/v1/chat/completions";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", false);
        body.put("temperature", 0.3);
        body.put("max_tokens", 2000);
        String effort = cfg.getReasoningEffort();
        if (effort != null && !effort.trim().isEmpty()) {
            body.put("reasoning_effort", effort.trim());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(cfg.getApiKey());

        try {
            ResponseEntity<JsonNode> resp = restTemplate.postForEntity(
                    url, new HttpEntity<>(body, headers), JsonNode.class);
            return extractContent(resp.getBody(), model);
        } catch (RestClientResponseException e) {
            String raw = e.getResponseBodyAsString();
            log.warn("DeepSeek 调用失败 model={} status={} body={}", model, e.getRawStatusCode(), raw);
            throw new BizException("模型调用失败(" + model + "): " + brief(raw, e.getStatusText()));
        } catch (Exception e) {
            log.warn("DeepSeek 调用异常 model={}", model, e);
            throw new BizException("模型调用异常(" + model + "): " + e.getMessage());
        }
    }

    private static String extractContent(JsonNode body, String model) {
        if (body == null) {
            throw new BizException("模型返回为空(" + model + ")");
        }
        JsonNode error = body.get("error");
        if (error != null && !error.isNull()) {
            throw new BizException("模型返回错误: " + error.path("message").asText());
        }
        JsonNode choices = body.path("choices");
        if (!choices.isArray() || choices.size() == 0) {
            throw new BizException("模型未返回结果(" + model + ")");
        }
        JsonNode message = choices.get(0).path("message");
        String content = message.path("content").asText("");
        if (content != null && !content.trim().isEmpty()) {
            return content.trim();
        }
        // 部分模型只返回推理内容
        String reasoning = message.path("reasoning_content").asText("");
        if (reasoning != null && !reasoning.trim().isEmpty()) {
            return reasoning.trim();
        }
        throw new BizException("模型返回内容为空(" + model + ")");
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }

    private static String brief(String text, String fallback) {
        if (text == null || text.trim().isEmpty()) {
            return fallback;
        }
        String t = text.trim();
        return t.length() > 300 ? t.substring(0, 300) + "…" : t;
    }
}
