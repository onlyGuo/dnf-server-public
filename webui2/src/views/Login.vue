<script setup lang="ts">
import {reactive} from "vue";
import Request from "../api/Request.ts";
import router from "../router";

const form = reactive({
  account: '',
  password: ''
});

// 重置表单
const resetForm = () => {
  form.account = '';
  form.password = '';
};

// 提交表单
const login = () => {
  Request.post("api/v1/login", form).then((res) => {
    localStorage.setItem("token", res.data);
    // 进入系统
    router.replace({
      path: router.currentRoute.value.query.redirect as string || "/admin/dashboard"
    })
  });
};
</script>

<template>
<div class="login-page">
  <div class="login-box">
    <div class="login-box-header">
      <img src="../assets/images/icon.png" />
      <h1>DNF 服务器管理平台</h1>
    </div>
    <div class="login-box-body">
      <a-form layout="vertical" :model="form">
        <a-form-item field="username">
          <a-input placeholder="请输入用户名" allow-clear v-model="form.account">
            <template #prefix>
              <icon-user />
            </template>
          </a-input>
        </a-form-item>
        <a-form-item field="password">
          <a-input-password placeholder="请输入密码" allow-clear v-model="form.password">
            <template #prefix>
              <icon-lock />
            </template>
          </a-input-password>
        </a-form-item>
        <a-form-item>
          <!-- 按钮: 重置+登录, 圆角, 居中 -->
          <div class="login-box-footer" style="display: flex; justify-content: center; gap: 10px;">
            <a-button class="btn" block shape="round" size="large" @click="resetForm">
              <icon-refresh /> 重置
            </a-button>
            <a-button class="btn" type="primary" block shape="round" size="large" @click="login">
              <icon-user /> 登录
            </a-button>
          </div>
        </a-form-item>
      </a-form>
    </div>
  </div>
</div>
</template>

<style scoped lang="less">
.login-page {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: url('../assets/images/login/login-bg2.png') no-repeat center center fixed;
  background-size: cover;

  .login-box {
    width: 340px;
    padding: 40px;
    background: rgba(255, 255, 255, 0.6);
    border-radius: 8px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    backdrop-filter: blur(10px);

    .login-box-header {
      text-align: center;
      margin-bottom: 30px;

      // 横向排列
      display: flex;
      flex-direction: row;
      align-items: center;
      justify-content: center;
      img {
        width: 50px;
        height: 50px;
        margin-right: 15px;
      }
      h1 {
        color: #333;
        margin: 0;
        font-size: 23px;
        font-weight: 700;
      }
    }

    .login-box-body {
      a-form-item {
        margin-bottom: 20px;
      }
      /deep/.arco-input-wrapper{
        background: rgba(216, 216, 216, 0.5);
      }
    }

    .login-box-footer{
      margin-top: 20px;
      text-align: center;
      display: flex;
      justify-content: center;
      width: 100%;
      gap: 10px;

      .btn {
        width: 45%;
      }
    }
  }
}
</style>
