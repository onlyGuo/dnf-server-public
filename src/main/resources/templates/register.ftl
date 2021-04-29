<!DOCTYPE html>
<html lang="zh-cn">
<head>
    <meta charset="UTF-8">
    <title>注册</title>
</head>
<body>
    <h1>写的匆忙，有点简陋，后期慢慢改</h1>
    账号:<input type="text" id="account"><br/>
    密码:<input type="password" id="password"><br/>
    确认:<input type="password" id="checkPassword"><br/>
    <button onclick="reg()">注册</button>
    <script type="text/javascript">
        location.href = 'http://8.140.189.172:9002/register';

        function reg(){
            let account = document.getElementById("account").value;
            let password = document.getElementById("password").value;
            let checkPassword = document.getElementById("checkPassword").value;
            if (!account){
                alert("请填写账号");
                return;
            }
            if (!password){
                alert("请填写密码");
                return;
            }
            if (!checkPassword || checkPassword !== password){
                alert("两次密码输入不一致");
                return;
            }
            location.href = "/client/register?accountname=" + account + "&password=" + password;
        }
    </script>
</body>
</html>