# 在线邮件调查

目前的邮件接口直接写入 `taiwan_cain_2nd.postal`。在线角色的邮箱由游戏进程持有，直接插入数据库后不一定重新读取，因此玩家可能需要重新选择角色。

公开项目 [dnf-admin](https://gitee.com/aredsun/dnf-admin/blob/master/README.md) 宣称支持不小退接收邮件，并使用 Frida 扩展；[eto-gateway 的邮件实现](https://github.com/ThePeppy/eto-gateway/blob/main/service/taiwan_cain_2nd/email.go) 则直接写入数据库，不能单凭该代码确认在线刷新能力。

本 DNF 项目的 `df_game_r` 带有符号，其中可以找到 `WongWork::CMailBoxHelper::ReqDBLoadMail`、`WongWork::CMailBox::AddNewMail` 和 `CMonitorServerProxy::SendNotifyNewMail`。这些符号说明存在可供进一步研究的游戏内部路径，但函数签名、调用线程、角色对象生命周期及通知后的邮箱行为尚未经过运行时验证，不能据此安全地直接调用。不同游戏二进制版本也可能不兼容。

因此暂未在 Web 中注入游戏进程或声称支持即时送达。后续需要针对当前游戏二进制和隔离的在线角色环境确认完整调用路径，再验证普通邮件、附件、离线接收和进程重启等情况。
