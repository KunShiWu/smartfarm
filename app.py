import json
import os
import sys
import secrets
import logging
import subprocess

from fastapi import FastAPI, Header, HTTPException, Depends
from pydantic import BaseModel
from mijiaAPI.apis import mijiaAPI
app = FastAPI(
    title="Mijia Smart Farm API",
    description="智慧农场米家设备控制服务",
    version="1.0.0"
)

# ==================================
# 1. 设备配置
# 后续新增同类开关设备，在这里添加
# ==================================

DEVICES = {
    "outdoor_pump": {
        "name": "室外抽水泵",
        "did": "2007541539",
        "model": "lxzn.switch.cbcsmj",
        "property": "on"
    }
}

# ==================================
# 2. API 身份验证
# ==================================

API_TOKEN = os.environ.get("MIJIA_BRIDGE_TOKEN")


def verify_token(
    token: str = Header(default="", alias="X-Bridge-Token")
):
    if not API_TOKEN:
        raise HTTPException(503, "API 密钥尚未配置")

    if not secrets.compare_digest(token, API_TOKEN):
        raise HTTPException(403, "访问密钥错误")


# ==================================
# 3. 调用 mijiaAPI
# ==================================

def run_mijia(*args):
    command = [
        sys.executable,
        "-m",
        "mijiaAPI",
        *args
    ]

    try:
        result = subprocess.run(
            command,
            capture_output=True,
            text=True,
            timeout=45,
            check=True
        )

        return result.stdout.strip()

    except subprocess.TimeoutExpired:
        raise HTTPException(504, "米家设备请求超时")

    except subprocess.CalledProcessError as e:
        logging.error(
            "mijiaAPI failed, exit code=%s, error=%s",
            e.returncode,
            (e.stderr or "")[-1000:]
        )
        raise HTTPException(502, "米家设备调用失败")


# ==================================
# 4. 查找已配置设备
# ==================================

def get_device(device_id):
    device = DEVICES.get(device_id)

    if device is None:
        raise HTTPException(404, "设备不存在")

    return device

def call_mijia_worker(operation, did, on=None):
    command = [
        sys.executable,
        "/app/miot_worker.py",
        operation,
        did
    ]

    if operation == "set":
        command.append("true" if on else "false")

    try:
        result = subprocess.run(
            command,
            capture_output=True,
            text=True,
            timeout=30,
            check=True
        )

        data = json.loads(result.stdout.strip())

    except subprocess.TimeoutExpired:
        raise HTTPException(
            status_code=504,
            detail="米家请求超时，设备状态需要重新确认"
        )

    except subprocess.CalledProcessError as e:
        logging.error(
            "米家请求失败: %s",
            (e.stderr or "")[-1000:]
        )
        raise HTTPException(
            status_code=502,
            detail="米家云端调用失败"
        )

    except json.JSONDecodeError:
        raise HTTPException(
            status_code=502,
            detail="米家返回数据格式错误"
        )

    if not isinstance(data, dict) or data.get("code") != 0:
        raise HTTPException(
            status_code=502,
            detail="米家设备请求未成功"
        )

    return data
# ==================================
# 5. 基础服务检查
# ==================================

@app.get("/health")
def health():
    return {
        "status": "ok",
        "service": "mijia-api"
    }


# ==================================
# 6. 获取设备列表
# ==================================

@app.get("/devices", dependencies=[Depends(verify_token)])
def list_devices():
    return {
        "devices": DEVICES
    }


# ==================================
# 7. 获取设备开关状态
# ==================================

@app.get(
    "/devices/{device_id}/status",
    dependencies=[Depends(verify_token)]
)
def get_status(device_id: str):
    device = get_device(device_id)

    result = call_mijia_worker(
        "get",
        device["did"]
    )

    if not isinstance(result.get("value"), bool):
        raise HTTPException(
            status_code=502,
            detail="设备返回的开关状态无效"
        )

    return {
        "device_id": device_id,
        "name": device["name"],
        "on": result["value"],
        "update_time": result.get("updateTime")
    }
# ==================================
# 8. 控制设备开关
# ==================================

class SwitchRequest(BaseModel):
    on: bool


@app.post(
    "/devices/{device_id}/switch",
    dependencies=[Depends(verify_token)]
)
def switch_device(device_id: str, request: SwitchRequest):
    device = get_device(device_id)

    result = call_mijia_worker(
        "set",
        device["did"],
        on=request.on
    )

    return {
        "device_id": device_id,
        "name": device["name"],
        "requested_on": request.on,
        "command_accepted": True,
        "actual_state_verified": False
    }
# ==================================
# 9. 兼容之前的水泵接口
# ==================================

@app.get("/pump/status", dependencies=[Depends(verify_token)])
def pump_status():
    return get_status("outdoor_pump")


@app.post("/pump/switch", dependencies=[Depends(verify_token)])
def pump_switch(request: SwitchRequest):
    return switch_device("outdoor_pump", request)
