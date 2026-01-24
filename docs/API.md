## 接口响应格式

后端统一返回 `Result<T>` 结构：

```json
{
  "success": true,
  "message": "操作成功",
  "data": {},
  "errorCode": "0"
}
```

说明：
- `success`: 是否成功
- `message`: 提示信息
- `data`: 业务数据
- `errorCode`: 响应码（成功为 `0`）

## 错误处理说明

- 统一返回 `HTTP 200` + `success=false` + `errorCode`，前端以 `success` 与 `errorCode` 判断业务错误
- 参数校验异常由全局异常处理器统一返回 `ERR-VALIDATION`

## 响应码

| code | 含义 |
| --- | --- |
| 0 | 成功 |
| 1000 | 通用错误 |
| 1001 | 参数校验失败 |
| 1004 | 资源不存在 |
| 1500 | 内部错误 |

