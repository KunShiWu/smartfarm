import os
import json

from pathlib import Path
from dotenv import load_dotenv
from openai import AsyncOpenAI
from mcp import Client
from uuid import UUID
from agent.prompts import AGRICULTURE_SYSTEM_PROMPT


# ========== 1. 配置初始化 ==========

PROJECT_DIR = Path(__file__).resolve().parents[1]

ENV_FILE = (
    PROJECT_DIR.parent
    / "config"
    / "runtime-secrets.env"
)

load_dotenv(ENV_FILE, override=False)

MCP_URL = "http://127.0.0.1:8001/mcp"

# 目前只允许数据查询类工具
ALLOWED_TOOLS = {
    "get_farm_hosts",
    "get_environment",
    "get_weather",
    "identify_crop_disease"
}


# ========== 2. Agent 类 ==========

class SmartFarmAgent:

    def __init__(self):

        api_key = os.getenv("SMARTFARM_DEEPSEEK_API_KEY")

        if not api_key:
            raise RuntimeError(
                "未读取到 SMARTFARM_DEEPSEEK_API_KEY"
            )

        self.llm = AsyncOpenAI(
            api_key=api_key,
            base_url="https://api.deepseek.com",
            timeout=60.0
        )

        self.model = "deepseek-flash"

        self.system_prompt = AGRICULTURE_SYSTEM_PROMPT

        self.max_rounds = 5

    # ========== 3. Agent 运行入口 ==========
    # 验证当前请求的图片ID

    async def run(
            self,
            user_question: str,
            history: list[dict] | None = None,
            image_id: str | None = None
    ) -> str:

        if not user_question.strip():
            return "请输入问题。"
        if image_id:
            try:
                image_id = str(UUID(image_id))
            except (ValueError, TypeError, AttributeError):
                raise ValueError("image_id 格式不正确")
        # 连接 MCP Server
        async with Client(
                MCP_URL,
                read_timeout_seconds=180.0
        ) as mcp_client:

            # 获取工具列表
            tools_result = await mcp_client.list_tools()

            # 将 MCP 工具转换为 LLM 工具格式
            llm_tools = []

            for tool in tools_result.tools:

                if tool.name not in ALLOWED_TOOLS:
                    continue

                # 没有上传图片时，不提供病害视觉识别工具
                if tool.name == "identify_crop_disease" and not image_id:
                    continue
                llm_tools.append({
                    "type": "function",
                    "function": {
                        "name": tool.name,
                        "description": tool.description or "",
                        "parameters": tool.input_schema
                    }
                })

            if not llm_tools:
                raise RuntimeError("没有可用的 MCP 工具")

            # ========== 4. 构建对话消息 ==========

            # 构建对话上下文
            messages = [
                {
                    "role": "system",
                    "content": self.system_prompt
                }
            ]
            # 告诉 Agent 本次请求已上传图片
            if image_id:
                messages.append({
                    "role": "system",
                    "content": (
                        "本次请求已包含一张用户上传的农作物图片。"
                        f"本次图片ID：{image_id}。"
                        "如果用户要求分析这张图片，应先调用"
                        "identify_crop_disease 工具获取视觉观察结果。"
                        "不能仅凭图片ID推测病害，不能将候选病因视为确诊。"
                        "如需结合环境信息，可以继续调用其他MCP工具。"
                    )
                })
            # 加载最近的对话历史
            for item in (history or [])[-10:]:
                if not isinstance(item, dict):
                    continue

                role = item.get("role")
                content = item.get("content")

                # 仅允许普通用户和助手消息
                if role not in ("user", "assistant"):
                    continue

                if not isinstance(content, str) or not content.strip():
                    continue

                messages.append({
                    "role": role,
                    "content": content[:2000]
                })

            # 添加当前用户问题
            messages.append({
                "role": "user",
                "content": user_question
            })

            # ========== 5. Agent 决策循环 ==========

            for round_num in range(1, self.max_rounds + 1):

                print(f"\n[Agent] 第 {round_num} 轮决策")

                response = await self.llm.chat.completions.create(
                    model=self.model,
                    messages=messages,
                    tools=llm_tools,
                    tool_choice="auto",
                    extra_body={
                        "thinking": {"type": "disabled"}
                    }
                )

                message = response.choices[0].message

                # 如果模型不再请求工具，返回最终答案
                if not message.tool_calls:

                    answer = message.content or "模型未返回内容"

                    print("[Agent] 任务完成")

                    return answer

                # 保存 assistant 的工具调用消息
                messages.append({
                    "role": "assistant",
                    "content": message.content,
                    "tool_calls": [
                        call.model_dump(exclude_none=True)
                        for call in message.tool_calls
                    ]
                })

                # ========== 6. 执行 MCP 工具 ==========

                for call in message.tool_calls:

                    tool_name = call.function.name

                    print(f"[MCP] 请求调用：{tool_name}")

                    try:

                        arguments = json.loads(
                            call.function.arguments or "{}"
                        )

                        if not isinstance(arguments, dict):
                            raise ValueError(
                                "工具参数必须是 JSON 对象"
                            )

                        # 工具权限验证
                        if tool_name not in ALLOWED_TOOLS:
                            raise ValueError(
                                f"未授权工具：{tool_name}"
                            )
                        # 病害识别只能使用本次请求绑定的图片
                        if tool_name == "identify_crop_disease":
                            if not image_id:
                                raise ValueError("当前请求没有上传图片")

                            # 不信任大模型自行生成的图片ID
                            arguments["image_id"] = image_id
                        # 调用 MCP Server
                        result = await mcp_client.call_tool(
                            tool_name,
                            arguments,
                            read_timeout_seconds=180.0
                        )

                        # 处理 MCP 返回内容
                        content = []

                        for item in result.content:
                            if item.type == "text":
                                content.append(item.text)

                        tool_output = {
                            "is_error": bool(result.is_error),
                            "data": "\n".join(content)
                        }

                        print(
                            f"[MCP] 工具执行结束，"
                            f"是否出错：{result.is_error}"
                        )

                    except Exception as e:

                        tool_output = {
                            "is_error": True,
                            "error": str(e)
                        }

                        print(f"[MCP] 工具调用异常：{e}")

                    # ========== 7. 工具结果回传 LLM ==========

                    messages.append({
                        "role": "tool",
                        "tool_call_id": call.id,
                        "content": json.dumps(
                            tool_output,
                            ensure_ascii=False
                        )
                    })

            return (
                "已达到最大决策轮数，"
                "任务尚未完全完成，请缩小查询范围后重试。"
            )