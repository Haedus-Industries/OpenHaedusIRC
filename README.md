# OpenHaedusIRC

Java 聊天与在线状态服务，与 [OpenArdion](https://github.com/wzhy233/OpenArdion) 配合使用。

## 构建和启动

需要 JDK 21 和 Maven。在项目目录执行：

```sh
mvn package
java -jar target/OpenHaedusIRC.jar
```

默认监听 `127.0.0.1:16667`。启动命令接受监听地址和端口两个参数：

```sh
java -jar target/OpenHaedusIRC.jar 127.0.0.1 16668
```

## 连接 OpenArdion

在游戏启动器中添加 JVM 参数：

```text
-Dopenhaedusirc.host=127.0.0.1 -Dopenhaedusirc.port=16667
```

进入游戏后开启 IRC 模块。服务支持聊天、在线列表、皮肤状态共享，以及 `users`、`version`、`help` 命令。

消息格式见 [PROTOCOL.md](PROTOCOL.md)，客户端代码位于 OpenArdion 的 `src/client/java/io/github/wzhy233/ardion/irc/`。

## 协议检查

构建后执行：

Linux：

```sh
java -ea -cp target/test-classes:target/OpenHaedusIRC.jar io.github.wzhy233.openhaedusirc.ProtocolCheck
```

Windows：

```powershell
java -ea -cp "target/test-classes;target/OpenHaedusIRC.jar" io.github.wzhy233.openhaedusirc.ProtocolCheck
```

## 许可

[MIT License](LICENSE)。
