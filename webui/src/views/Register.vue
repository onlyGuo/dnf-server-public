<template>
    <div>
        <div class="nav-con">
            <nav-bar></nav-bar>
        </div>
        <div class="box">
            <h1>注册账号</h1>

            <el-form ref="form" :model="form" label-width="80px">
                <el-form-item label="电子邮箱">
                    <el-input v-model="form.accountname" placeholder="请输入电子邮箱"></el-input>
                </el-form-item>
                <el-form-item label="图形码">
                    <el-input v-model="form.valicode" placeholder="请输入图形验证码" style="width: calc(100% - 100px)"></el-input>
                    <img :src="'/api/v1/vc/img/' + form.validationIndex" @click="form.validationIndex = uuid()"
                         style="height: 35px; width: 98px; vertical-align: top;border: slategray solid 1px">
                </el-form-item>
<!--                <el-form-item label="验证码">-->
<!--                    <el-input v-model="form.smsCode" placeholder="请输入短信验证码" style="width: calc(100% - 100px)"></el-input>-->
<!--                    <el-button @click="sendSms" type="primary" style="width: 100px;">{{ sendBtnText }}</el-button>-->
<!--                </el-form-item>-->
                <el-form-item label="密码">
                    <el-input v-model="form.password" placeholder="请输入登录密码" type="password"></el-input>
                </el-form-item>
                <el-form-item label="确认密码">
                    <el-input v-model="form.checkPassword" placeholder="请重新输入登录密码" type="password"></el-input>
                </el-form-item>

<!--                <p style="text-align: center; color: gray">-->
<!--                    注册后用手机号码即可登录！-->
<!--                </p>-->
<!--                <br/>请善待您的账号，避免消极游戏行为，每个手机号只能注册一次。-->
<!--                <br/>不要相信类似【付费解封】之类的信息，以防上当受骗。-->
<!--                <br/>游戏中遇到自称官方人员时，请确认对方是否佩戴<span style="font-weight: bold; color: black;">[DNF运营商]</span>称号。-->
<!--                <br/><br/>-->

              <p style="text-align: center; color: gray">
                注册后用邮箱即可登录！
              </p>
              <br/>同时，该账号也是GM后台账号，您可以通过上方的“管理员入口”进入GM后台。
              <br/>所有物品均可通过后台直接获得，同时，您还可以在GM后台创建您朋友的小号一起在游戏里开黑，n祝您游戏愉快！

              <br/><br/>

                <el-button type="primary" @click="register">立即注册</el-button>
            </el-form>
        </div>
    </div>
</template>

<script>
import NavBar from "../components/NavBar.vue";
import router from "../router/index.js";
import api from "../libs/api.js";

export default {
    name: "Register",
    components: {NavBar},
    data(){
        return {
            form: {
                accountname: '',
                valicode: '',
                smsCode: '',
                password: '',
                checkPassword: '',
                validationIndex: this.uuid()
            },
            sendBtnText: '发送'
        }
    },
    methods: {
        sendSms(){
            if(this.waiting > 0){
                return;
            }
            if (!this.form.accountname){
                this.$message.error("请填写邮箱");
                return;
            }
            if (!this.form.valicode){
                this.$message.error("请填写图形验证码");
                return;
            }
            // this.ser.post("vc/sms", {
            //     code: this.form.valicode,
            //     index: this.valiId,
            //     phone: this.form.accountname,
            //     type: "Register"
            // }).then(() => {
            //     this.$message.success("短信发送成功")
            //     this.waiting = 60;
            //     this.goWaiting();
            // });
        },
        goWaiting(){
            if (this.waiting > 0){
                this.sendBtnText = '发送(' + this.waiting + 's)'
                this.waiting --;
                setTimeout(()=> {
                    this.goWaiting()
                }, 1000);
            }else{
                this.sendBtnText = '发送';
            }
        },
        register(){
            if (!this.form.accountname){
                this.$message.error("请填写邮箱");
                return;
            }
            if (this.form.checkPassword !== this.form.password){
                this.$message.error("两次密码输入不一致");
                return;
            }
            api.post("account", this.form).then(() => {
              this.$message.success("注册成功");
              setTimeout(() => {
                router.replace({
                  path: '/'
                })
              }, 2000)
            })
        },
        uuid(){
          let s = [];
          let hexDigits = "0123456789abcdef";
          for (let i = 0; i < 36; i++) {
            s[i] = hexDigits.substr(Math.floor(Math.random() * 0x10), 1);
          }
          s[14] = "4";  // bits 12-15 of the time_hi_and_version field to 0010
          s[19] = hexDigits.substr((s[19] & 0x3) | 0x8, 1);  // bits 6-7 of the clock_seq_hi_and_reserved to 01
          s[8] = s[13] = s[18] = s[23] = "-";
          let uuid = s.join("");
          return uuid.replace("-", "");
        }
    }
}
</script>

<style scoped lang="less">
    .nav-con{
        background-color: rgb(84, 92, 100);
        border-bottom: solid 1px #e6e6e6;
        height: 60px;
    }
    .box{
        width: 500px;
        min-height: 100px;
        margin: 100px auto;
        text-align: center;
    }
</style>