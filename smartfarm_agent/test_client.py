
import asyncio
from mcp import Client

# MCP Server 地址
MCP_URL = "http://127.0.0.1:8001/mcp"


async def main():
    # 第一步：建立 MCP 连接
    async with Client(MCP_URL) as client:
        print("MCP Server 连接成功")

        # 第二步：获取服务器注册的工具
        tools_result = await client.list_tools()

        print("\n发现的 MCP 工具：")
        for tool in tools_result.tools:
            print(f"工具名称：{tool.name}")
            print(f"工具描述：{tool.description}")

        # 第三步：调用指定工具
        result = await client.call_tool(
            "identify_crop_disease",
            {
                "image_id": "6dde4f71-fc0e-477d-83b4-ace0fef17ccc"
            }
        )

        print("\n工具执行结果：")
        print("是否出错：", result.is_error)

        for item in result.content:
            if item.type == "text":
                print(item.text)


if __name__ == "__main__":
    asyncio.run(main())
