<template>
    <div>
        <div class="nav-con">
            <nav-bar></nav-bar>
        </div>

        <div class="box">
            <h1>充值/兑换</h1>
            <el-form ref="form" :model="form" label-width="80px">
                <el-form-item label="账号">
                    <el-input v-model="form.useAccount" placeholder="请输入手机号码" @blur="listCharachs"></el-input>
                </el-form-item>
                <div class="loading-charach" v-if="charachDislabel">
                    <span>正在获取角色列表，请稍后</span>
                </div>
                <el-form-item label="选择角色">
                    <el-select v-model="form.charach" placeholder="轻选择角色" style="width: 100%"
                               :disabled="charachDislabel">
                        <el-option :label="charach.characName" :value="charach.characNo"
                                   v-for="charach in charachList" v-bind:key="charach.characNo"></el-option>
                    </el-select>
                </el-form-item>

                <el-form-item label="图形码">
                    <el-input v-model="form.valicode" placeholder="请输入图形验证码" style="width: calc(100% - 100px)"></el-input>
                    <img :src="'/api/v1/vc/img/' + form.valiId" @click="form.valiId = uuid()"
                         style="height: 35px; width: 98px; vertical-align: top;border: slategray solid 1px">
                </el-form-item>
                <el-form-item label="充值卡">
                    <el-input v-model="form.content" placeholder="请输入CDK或卡密"></el-input>
                </el-form-item>

                <p style="text-align: center; color: gray">
                    充值点券后任意角色可使用，兑换CDK后重新登录即可收到
                </p>
                <br/>请善待您的账号，避免消极游戏行为，每个手机号只能注册一次。
                <br/>不要相信类似【付费解封】之类的信息，以防上当受骗。
                <br/>游戏中遇到自称官方人员时，请确认对方是否佩戴<span style="font-weight: bold; color: black;">[DNF运营商]</span>称号。
                <br/><br/>

                <el-button type="primary" @click="doRecharge" :loading="loading">充 值</el-button>
            </el-form>
        </div>
    </div>
</template>

<script>
import NavBar from "../components/NavBar";
export default {
    name: "Recharge",
    components: {NavBar},
    data(){
        return {
            form: {
                useAccount: '',
                content: '',
                charach: '',
                valicode: '',
                valiId: this.uuid()
            },
            charachList: [],
            charachDislabel: false,
            loading: false
        }
    },
    methods: {
        listCharachs(){
            if (this.form.useAccount){
                this.charachDislabel = true
                this.ser.get("charac/" + this.form.useAccount).then(res => {
                    this.charachList = res.data;
                    if (this.charachList.length > 0){
                        this.form.charach = this.charachList[0].characNo;
                    }
                }).finally(() => {
                    this.charachDislabel = false
                })
            }else{
                this.charachList = []
                this.form.charach = ''
            }
        },
        doRecharge(){
            if (!this.form.content){
                this.$message.error("卡密无效");
            }
            this.loading = true;
            this.ser.post("keys/use", this.form).then(() => {
                this.$message.success("充值成功");
            }).finally(() => {
                this.loading = false;
            })
        }
    }
}
</script>

<style scoped lang="less">
.box{
    width: 500px;
    min-height: 100px;
    margin: 100px auto;
    text-align: center;
}
.loading-charach{
    background-color: rgba(255, 255, 255, 0.5);
    height: 40px;
    position: fixed;
    width: 500px;
    z-index: 1;

    span {
        line-height: 40px;
        cursor: default;
    }
}
</style>