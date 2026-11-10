
import logging

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from typing import Literal
from agent.runner import SmartFarmAgent
from uuid import UUID

# ========== 1. 创建 FastAPI 应用 ==========

app = FastAPI(
    title="SmartFarm Agent API",
    description="智慧农场智能体 HTTP 接口",
    version="1.0.0"
)

logger = logging.getLogger(__name__)

# 初始化 Agent
agent = SmartFarmAgent()


# ========== 2. 定义请求和响应结构 ==========

class HistoryMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(
        min_length=1,
        max_length=2000
    )


class ChatRequest(BaseModel):
    message: str = Field(
        min_length=1,
        max_length=2000,
        description="用户当前输入的问题"
    )

    history: list[HistoryMessage] = Field(
        default_factory=list,
        max_length=10,
        description="最近10条历史对话消息"
    )

    image_id: UUID | None = Field(
        default=None,
        description="用户上传的农作物图片ID，可选"
    )

class ChatResponse(BaseModel):
    answer: str


# ========== 3. 健康检查接口 ==========

@app.get("/health")
async def health():
    return {
        "status": "ok",
        "service": "smartfarm-agent"
    }


# ========== 4. Agent 对话接口 ==========

@app.post(
    "/agent/chat",
    response_model=ChatResponse
)
async def chat(request: ChatRequest):

    try:
        answer = await agent.run(
            user_question=request.message,
            history=[
                item.model_dump()
                for item in request.history
            ],
            image_id=(
                str(request.image_id)
                if request.image_id is not None
                else None
            )
        )

        return ChatResponse(answer=answer)

    except Exception:
        logger.exception("Agent 调用失败")

        raise HTTPException(
            status_code=502,
            detail="Agent 调用失败，请检查服务日志"
        )
