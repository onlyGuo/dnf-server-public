# dnf-server-public

#### 介绍
这是一个脱敏后的DOF后台管理系统，为了保证作者自身的服务安全，部分敏感数据不对外纰漏，而通过远程静默授权的方式从作者服务器读取。
除此之外所有代码均可随意查看或修改。

本想直接编译出一个现成的jar包给大家用，但实际考虑后还是决定开源出来，毕竟即使从源码出发，整体的安装过程也不算复杂，并且某些时候水很深。

#### 软件架构
spring-boot


#### 安装过程

1. 安装JDK1.8环境和Maven编译工具并配置环境变量
2. 拉取本仓库代码。
3. 修改你的数据库配置和短信配置：`src/main/resources/application.yml`:
```` yml
server:
  port: 9001
spring:
  application:
    name: DNF Service Web Application
  datasource:
    url: jdbc:mysql://这里改成你的数据库地址:端口号?useUnicode=true&characterEncoding=latin1
    password: 这里填写你的数据库密码
    username: 这里填写你的数据库账号
    driver-class-name: com.mysql.jdbc.Driver
  freemarker:
    suffix: .ftl
mybatis:
  mapper-locations: classpath*:mappers/*.xml
  type-aliases-package: com.aiyi.game.dnfserver.entity
logging:
  file: logs/dnf.log
  level:
    com.aiyi: debug

aliyun:
  access-key-id: 这里填写你从阿里云申请的access-key-id 否则注册时无法发送短信
  access-secret: 这里填写你从阿里云申请的access-secret 否则注册时无法发送短信
  oss:
    endpoint: 这里不用管，预留，后续制作全自动更新版本时可能用得上
    bucket-name: 这里不用管，预留，后续制作全自动更新版本时可能用得上
````
4. 编辑`/src/main/java/com/aiyi/game/dnfserver/service/rmt/RmtService.java`, 修改第67、68设置你的管理员账号密码，默认都是admin，请务必改为自己的，如下图：
![rsa](https://github.com/onlyGuo/dnf-server-public/raw/main/doc/default-admin.png)

5. 生成独属于自己的公私钥（长度:2048bit 格式:PKCS#8）,可以在这里在线生成：http://www.metools.info/code/c80.html 如下图:

   -- 为什么不把公私钥直接内置到程序中而是强制你手动生成？因为公私钥时DOF服务端与客户端通信加密的基础，每个人用不同的密钥可以提高安全性，若都用统一的密钥，那么别有用心的人稍微使点手段就可以跳过密码验证直接登录你的私服，导致玩家账号密码形同虚设。
![rsa](https://github.com/onlyGuo/dnf-server-public/raw/main/doc/rsa.png)

6. 用`notpad++`或`vs code`等一类的工具将公钥保存为`publickey.pem`并上传到服务器game目录。
![rsa](https://github.com/onlyGuo/dnf-server-public/raw/main/doc/rsa-pub.png)
![rsa](https://github.com/onlyGuo/dnf-server-public/raw/main/doc/rsa-pub-upload.png)

7. 复制私钥内容（不要复制`-----BEGIN PRIVATE KEY-----`和`-----END PRIVATE KEY-----`这一类的头尾标识）到文件`src/main/resources/private.key` 替换里面的content，如下如:
![rsa](https://github.com/onlyGuo/dnf-server-public/raw/main/doc/rsa-pri.png)

8. 在根目录下执行cmd命令`mvn clean package`进行编译（初次编译会下载各种依赖包，会很慢，约30分钟，耐心等待，网络越好速度越快，若编译失败可以使用‘你懂得的上网方案’）。

9. 观察日志出现如下内容标识编译完成，编译成品为：`target/dnf-server-0.0.1-SNAPSHOT.jar`
````
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  7.395 s
[INFO] Finished at: 2021-04-29T16:14:55+08:00
[INFO] ------------------------------------------------------------------------
````
10. 服务器安装JDK8环境，将`target/dnf-server-0.0.1-SNAPSHOT.jar`上传至服务器
11. 在服务器jar包目录下执行: `nohup java -jar dnf-server-0.0.1-SNAPSHOT.jar &`
12. 配置前端页面：https://github.com/onlyGuo/dnf-server-web-public.git

#### 使用说明

1. 启动后在当前目录执行`tail -f logs/dnf.log`可以查看日志以便于排错。
2. 结束本服务进程执行`pkill java`
3. 联系QQ：719348277
