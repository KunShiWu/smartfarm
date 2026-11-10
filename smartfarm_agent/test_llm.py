import os
from pathlib import Path
from dotenv import load_dotenv
from openai import OpenAI

# 获取当前Python文件所在目录
PROJECT_DIR = Path(__file__).resolve().parent

# 定位到统一环境变量文件
ENV_FILE = PROJECT_DIR.parent / "config" / "runtime-secrets.env"

# 检查环境文件是否存在
if not ENV_FILE.is_file():
    raise FileNotFoundError(f"环境变量文件不存在: {ENV_FILE}")

# 加载环境变量
load_dotenv(dotenv_path=ENV_FILE, override=False)

# 读取已有环境变量
api_key = os.getenv("SMARTFARM_DEEPSEEK_API_KEY")

if not api_key:
    raise RuntimeError("未读取到 SMARTFARM_DEEPSEEK_API_KEY")

# 创建大模型客户端
client = OpenAI(
    api_key=api_key,
    base_url="https://api.deepseek.com",
    timeout=30.0
)

# 调用大模型
response = client.chat.completions.create(
    model="deepseek-flash",
    messages=[
        {
            "role": "system",
            "content": "你是智慧农场农业助手，回答应准确、简洁，不得编造监测数据。"
        },
        {
            "role": "user",
            "content": "请简单介绍你可以为智慧农场提供哪些帮助。"
        }
    ]
)

print("大模型回答：")
print(response.choices[0].message.content)