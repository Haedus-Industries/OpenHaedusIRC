# OpenHaedusIRC 协议

TCP 连接使用 UTF-8 编码，每行一条 JSON 消息。

## 登录

客户端发送：

```json
{"type":"login","service":"OpenHaedusIRC","version":"000","username":"PlayerOne"}
```

服务端回复：

```json
{"type":"welcome","service":"OpenHaedusIRC","version":"000","username":"PlayerOne"}
```

`username` 为 3–16 位字母、数字或下划线，昵称比较时忽略大小写。重复昵称、服务名或版本不匹配时，服务端发送 `error` 并关闭连接。

## 聊天

客户端发送：

```json
{"type":"chat","content":"Hello"}
```

服务端广播：

```json
{"type":"message","sender":"PlayerOne","permission":"User","content":"Hello","timestamp":0}
```

`timestamp` 为服务端生成的 Unix 毫秒时间戳，`permission` 为 `User`。聊天内容最多 512 字符。

## 在线状态

客户端更新状态：

```json
{"type":"presence","content":{"mcName":"PlayerOne","uuid":"","skin-name":"","customSkin":false,"cape":true,"model":"auto"}}
```

| 字段 | 格式 |
| --- | --- |
| `mcName` | 0–16 位字母、数字或下划线 |
| `uuid` | 标准 UUID，或空字符串 |
| `skin-name` | 3–16 位玩家名，或空字符串 |
| `customSkin` | 布尔值；为 `false` 时 `skin-name` 为空 |
| `cape` | 布尔值 |
| `model` | `auto`、`slim` 或 `wide` |

登录、状态变化和离线时，服务端广播 `onlineUsers`。`content` 为用户对象数组，每个对象包含 `username`、`permission` 和表中的状态字段。

## 心跳和命令

客户端发送 `{"type":"ping"}`，服务端回复 `{"type":"pong"}`。

命令示例：

```json
{"type":"command","command":"version","arguments":[]}
```

支持 `users`、`version` 和 `help`。文本回复使用 `system`，错误使用 `error`：

```json
{"type":"system","content":"OpenHaedusIRC 000"}
{"type":"error","content":"Unknown command"}
```

## 连接限制

| 项目 | 数值 |
| --- | --- |
| 单帧长度 | 最多 16,384 字符 |
| 登录超时 | 10 秒 |
| 登录后空闲超时 | 30 秒 |
| 连接数 | 最多 64 个 |
| 每个连接的发送频率 | 每秒最多 20 个数据包 |

未知数据包、错误字段、超长帧和超频输入会关闭连接。未知命令返回 `error`。
