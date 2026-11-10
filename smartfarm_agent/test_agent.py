import os
import json
import asyncio

from pathlib import Path
from dotenv import load_dotenv
from openai import AsyncOpenAI
from mcp import Client

from agent.prompts import AGRICULTURE_SYSTEM_PROMPT


# ========== 1. 加载环境变量 ==========

PROJECT_DIR = Path(__file__).resolve().parent
ENV_FILE = PROJECT_DIR.parent / "config" / "runtime-secrets.env"

load_dotenv(ENV_FILE)

api_key = os.getenv("SMARTFARM_DEEPSEEK_API_KEY")

if not api_key:
    raise RuntimeError("DeepSeek API Key 未加载")


# ========== 2. 初始化 LLM ==========

llm = AsyncOpenAI(
    api_key=api_key,
    base_url="https://api.deepseek.com",
    timeout=60.0
)

MCP_URL = "http://127.0.0.1:8001/mcp"

# 第一版只允许读取农场数据
ALLOWED_TOOLS = {
    "get_farm_hosts",
    "get_environment",
    "get_weather"
}


async def main():

    # ========== 3. 连接 MCP Server ==========

    async with Client(MCP_URL) as mcp_client:

        print("MCP Server 连接成功")

        result = await mcp_client.list_tools()

        # ========== 4. 注册 LLM 工具 ==========

        llm_tools = []

        for tool in result.tools:

            if tool.name not in ALLOWED_TOOLS:
                continue

            llm_tools.append({
                "type": "function",
                "function": {
                    "name": tool.name,
                    "description": tool.description or "",
                    "parameters": tool.input_schema
                }
            })

        print("\n可供 LLM 使用的工具：")

        for tool in llm_tools:
            print(tool["function"]["name"])

        # ========== 5. 创建对话 ==========

        user_question = (
            "请先查询我的智慧农场有哪些设备，"
            "从设备列表中找到土壤监测主机的ID，"
            "然后查询该设备的土壤温度、湿度和pH值。"
            "请同时说明设备是否在线、数据采集时间，"
            "如果设备离线或数据过期，不要把它当作当前实时数据。"
        )
        messages = [
            {
                "role": "system",
                "content": AGRICULTURE_SYSTEM_PROMPT
            },
            {
                "role": "user",
                "content": user_question
            }
        ]

        # ========== 6. Agent 执行循环 ==========

        for round_num in range(1, 5):

            print(f"\n========== 第 {round_num} 轮 ==========")

            response = await llm.chat.completions.create(
                model="deepseek-flash",
                messages=messages,
                tools=llm_tools,
                tool_choice="auto",
                extra_body={
                    "thinking": {"type": "disabled"}
                }
            )

            message = response.choices[0].message

            # 情况一：模型不需要继续调用工具
            if not message.tool_calls:

                print("\nAgent 最终回答：")
                print(message.content or "模型未返回文本")
                return

            # 情况二：模型决定调用工具
            # 保存模型发出的工具调用请求
            messages.append({
                "role": "assistant",
                "content": message.content,
                "tool_calls": [
                    call.model_dump(exclude_none=True)
                    for call in message.tool_calls
                ]
            })

            # ========== 7. 执行 MCP 工具 ==========

            for call in message.tool_calls:

                tool_name = call.function.name

                print(f"\nLLM 选择工具：{tool_name}")
                print(f"原始参数：{call.function.arguments}")

                try:
                    # 将 JSON 字符串转换成 Python 字典
                    arguments = json.loads(
                        call.function.arguments or "{}"
                    )

                    if not isinstance(arguments, dict):
                        raise ValueError("工具参数必须是 JSON 对象")

                    # 防止调用未授权工具
                    if tool_name not in ALLOWED_TOOLS:
                        raise ValueError(
                            f"不允许调用工具：{tool_name}"
                        )

                    # 真正执行 MCP 工具
                    tool_result = await mcp_client.call_tool(
                        tool_name,
                        arguments
                    )

                    # 提取工具返回的文本
                    tool_text = "\n".join(
                        item.text
                        for item in tool_result.content
                        if item.type == "text"
                    )

                    if not tool_text:
                        tool_text = tool_result.model_dump_json(
                            exclude_none=True
                        )

                    tool_payload = {
                        "is_error": bool(tool_result.is_error),
                        "data": tool_text
                    }

                except Exception as e:

                    tool_payload = {
                        "is_error": True,
                        "data": str(e)
                    }

                print("工具执行完成")
                print(
                    json.dumps(
                        tool_payload,
                        ensure_ascii=False
                    )[:500]
                )

                # ========== 8. 将工具结果返回 LLM ==========

                messages.append({
                    "role": "tool",
                    "tool_call_id": call.id,
                    "content": json.dumps(
                        tool_payload,
                        ensure_ascii=False
                    )
                })

        print("\n已达到最大执行轮数，Agent 停止运行。")


if __name__ == "__main__":
    asyncio.run(main())