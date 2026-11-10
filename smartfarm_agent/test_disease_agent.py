
import asyncio
from agent.runner import SmartFarmAgent

async def main():
    agent = SmartFarmAgent()

    # 替换为之前上传图片得到的真实UUID
    image_id = "6dde4f71-fc0e-477d-83b4-ace0fef17ccc"

    answer = await agent.run(
        user_question=(
            "请分析我上传的农作物图片，"
            "描述可见病害症状、可能的病因，"
            "并说明是否需要补充环境信息。"
        ),
        image_id=image_id
    )

    print("\n========== 病害诊断结果 ==========")
    print(answer)

if __name__ == "__main__":
    asyncio.run(main())