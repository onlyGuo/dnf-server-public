<template>
    <div>
        <el-breadcrumb separator="/" style="margin-bottom: 30px">
            <el-breadcrumb-item>首页</el-breadcrumb-item>
            <el-breadcrumb-item>内容管理</el-breadcrumb-item>
            <el-breadcrumb-item>通知公告</el-breadcrumb-item>
        </el-breadcrumb>

        <div>
            <p>本页内容将作为公告展示在登陆器中。</p>

            <el-input
                type="textarea"
                :rows="6"
                placeholder="请输入公告内容"
                resize="none"
                maxlength="4096"
                v-model="textarea">
            </el-input>

            <el-button type="primary" @click="update">保存公告</el-button>
        </div>
    </div>
</template>

<script>
export default {
    name: "MgrNotice",
    data(){
        return {
            textarea: ''
        }
    },
    mounted() {
        this.ser.get("notice").then(res => {
            this.textarea = res.data.content;
        })
    },methods: {
        update(){
            this.ser.put('notice', {
                id: 1,
                content: this.textarea
            }).then(() => {
                this.$message.success("更新成功");
            });
        }
    }
}
</script>

<style scoped>

</style>