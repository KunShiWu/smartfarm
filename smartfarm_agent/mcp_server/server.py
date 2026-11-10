
import httpx
from uuid import UUID
from mcp.server import MCPServer

mcp = MCPServer("SmartFarm-MCP")

BACKEND_URL = "http://127.0.0.1:8080/api"


@mcp.tool()
def get_farm_status() -> dict:
    """查询MCP测试服务状态。"""
    return {
        "farm_name": "智慧农场",
        "status": "running",
        "message": "MCP 工具调用成功（模拟数据）"
    }


@mcp.tool()
def get_farm_hosts() -> list[dict]:
    """
    查询智慧农场设备列表。

    返回设备ID、名称、分组、在线状态以及设备类型。
    只读取数据，不执行设备控制。
    """
    with httpx.Client(timeout=20.0) as client:
        response = client.get(
            f"{BACKEND_URL}/hosts"
        )
        response.raise_for_status()

        data = response.json()

        if not isinstance(data, list):
            raise ValueError("主机列表接口返回格式异常")

        return data
@mcp.tool()
def get_environment(host_id: str) -> dict:
    """
    查询指定农场主机的环境监测数据。

    Args:
        host_id: 农场主机ID，例如4627。

    Returns:
        主机状态、数据采集时间及环境参数。
    """
    if not host_id.isdigit():
        raise ValueError("host_id 必须是数字ID")

    with httpx.Client(timeout=20.0) as client:
        response = client.get(
            f"{BACKEND_URL}/hosts/{host_id}/realtime"
        )
        response.raise_for_status()
        return response.json()
@mcp.tool()
def get_weather() -> dict:
    """
    查询智慧农场所在地的天气信息。

    返回天气状况、气温、空气湿度、风向、
    风力、发布时间以及天气预警等信息。

    只查询天气数据，不控制农场设备。
    """
    with httpx.Client(timeout=20.0) as client:
        response = client.get(
            f"{BACKEND_URL}/weather"
        )

        response.raise_for_status()
        return response.json()


@mcp.tool()
def identify_crop_disease(image_id: str) -> dict:
    """
    根据用户上传的农作物图片进行病害视觉识别。

    使用场景：
        用户上传农作物图片，并希望识别病害、
        分析叶片异常或判断可能的病因。

    Args:
        image_id: 图片上传后由Spring Boot生成的UUID。

    Returns:
        作物名称、可见病害症状、候选病因、
        不确定性说明及需要补充的信息。

    注意：
        本工具只提供视觉观察和候选病因，
        不能将结果直接视为病害确诊。
    """

    # 1. 验证图片ID格式
    try:
        image_id = str(UUID(image_id))
    except (ValueError, AttributeError, TypeError):
        raise ValueError("image_id 必须是有效的UUID")

    # 2. 请求Spring Boot病害识别接口
    with httpx.Client(
        timeout=httpx.Timeout(180.0, connect=5.0)
    ) as client:

        response = client.post(
            f"{BACKEND_URL}/farm/observe-by-id",
            json={
                "image_id": image_id
            }
        )

        response.raise_for_status()

        # 3. 解析视觉模型结果
        data = response.json()

        if not isinstance(data, dict):
            raise ValueError("病害识别接口返回格式异常")

        # 4. 检查是否真正使用了图片
        if data.get("usedImage") is not True:
            raise ValueError("视觉模型未成功处理图片")

        return data

if __name__ == "__main__":
    mcp.run(
        transport="streamable-http",
        host="127.0.0.1",
        port=8001
    )
