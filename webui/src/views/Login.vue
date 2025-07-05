<template>
    <div class="login-box-parent">
      <div class="login-box">
        <div class="login-box-bg">
          <div class="login-box-bg-content">
            <div style="font-size: 18px; line-height: 50px">欢迎使用GM后台</div>
            <div style="padding: 10px">
              客服邮箱：719348277@qq.com
            </div>
          </div>
        </div>

        <div class="login-box-content">
          <el-form ref="form" :model="form" label-width="40px">
            <div class="login-form-title">登录</div>
            <el-form-item label="账号">
              <el-input v-model="form.account"></el-input>
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="form.password" type="password"></el-input>
            </el-form-item>
            <div style="text-align: center">
              <el-button style="width: 200px; margin: auto" type="primary" @click="onSubmit">登录</el-button>
            </div>
          </el-form>
        </div>
      </div>
    </div>
</template>

<script>
import api from "../libs/api.js";
import router from "../router/index.js";

export default {
    name: "Login",
    data(){
        return {
            form: {
                account: '',
                password: ''
            },
          ser: api
        }
    },
    methods: {
        onSubmit(){
            this.ser.post("login", this.form).then(res => {
                window.localStorage.setItem("token", res.data);
                // this.go("/manager");
              router.replace({
                path: '/manager'
              })
            })
        }
    }
}
</script>

<style scoped lang="less">
.login-box-parent{
  display: flex;
  justify-content: center; /* 水平居中 */
  align-items: center;    /* 垂直居中 */
  height: 100vh;          /* 让父元素高度充满视口，或根据需要设置高度 */
  background: rgba(0,0,0,.1);

  .login-box{
    height: 300px;
    width: 500px;
    position: relative;

    .login-box-bg{
      background: #2c3e50;
      height: 200px;
      width: 100%;
      position: absolute;
      top: 50px;
      .login-box-bg-content{
        width: 170px;
        height: 100%;
        color: white;
        text-align: center;
        margin-top: 30px;
      }
    }
    .login-box-content{
      background: white;
      height: 260px;
      width: 300px;
      position: absolute;
      right: 15px;
      top: 10px;
      padding: 10px;
      border-radius: 10px;
      overflow: hidden;
      display: flex;
      justify-content: center; /* 水平居中 */
      align-items: center;    /* 垂直居中 */
      box-shadow: #242424 2px 2px 10px -5px;

      .login-form-title{
        text-align: center;
        margin-bottom: 20px;
      }
    }
  }
}
</style>