<script>
import api from "../../../libs/api.js";

export default {
  name: "EditCharach",
  props: {
    options: {
      type: Object,
      default: {
        show: false,
        form: {
          characNo: 0,
          characName: '',
          accountname: '',
          lev: 0,
          growType: '',
          maxHp: 0,
          maxMp: 0,
          phyAttack: 0,   // 物攻
          phyDefense: 0,  // 物防
          magAttack: 0,   // 魔攻
          magDefense: 0,  // 魔防
          attackSpeed: 0, // 攻速
          castSpeed: 0,   // 施速
          moveSpeed: 0,   // 移速
          hitRecovery: 0, // 硬直
          jump: 0,        // 跳跃
          fatigue: 0,     // 疲劳
        }
      }
    },
    onSave: {
      type: Function,
      default: () => {}
    }
  },
  data(){
    return {
      
    }
  },
  methods: {
    save(){
      this.options.form.job = undefined;
      this.options.form.growType = undefined;
      api.put('charac', this.options.form).then(res => {
        this.$message.success("修改成功，请上游戏查看");
        this.options.show = false;
        this.onSave();
      })
    }
  }
}

</script>

<template>
<div>
  <el-dialog title="编辑角色属性" v-model="options.show">
    <div style="margin-bottom: 20px; font-size: 14px; margin-top: -10px">
      PS: 修改角色属性时，请确保当前角色一退出登录，否则修改无效。
    </div>
    <el-form label-width="50">
      <div class="form-classify">
        <div class="form-classify-title">
          角色基础信息
        </div>
        <div class="form-classify-content">
          <el-row>
            <el-col :span="12">
              <el-form-item label="昵称" style="margin-right: 10px">
                <el-input placeholder="请输入角色名称" disabled v-model="options.form.characName"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="等级">
                <el-input placeholder="角色等级" disabled v-model="options.form.lev"></el-input>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row>
            <el-col :span="12">
              <el-form-item label="职业" style="margin-right: 10px">
                <el-input placeholder="请输入角色名称" disabled v-model="options.form.job"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="转职">
                <el-input placeholder="请输入角色名称" disabled v-model="options.form.growType"></el-input>
              </el-form-item>
            </el-col>
          </el-row>
        </div>
      </div>


      <div class="form-classify">
        <div class="form-classify-title">
          角色基础属性
        </div>
        <div class="form-classify-content">
          <el-row>
            <el-col :span="12">
              <el-form-item label="HP" style="margin-right: 10px">
                <el-slider v-model="options.form.maxHp" :min="1" :max="65535" :step="1"/>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="MP">
                <el-slider v-model="options.form.maxMp" :min="1" :max="65535" :step="1"/>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row>
            <el-col :span="12">
              <el-form-item label="物攻" style="margin-right: 10px">
                <el-slider v-model="options.form.phyAttack" :min="1" :max="65535" :step="1"/>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="物防">
                <el-slider v-model="options.form.phyDefense" :min="1" :max="65535" :step="1"/>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row>
            <el-col :span="12">
              <el-form-item label="魔攻" style="margin-right: 10px">
                <el-slider v-model="options.form.magAttack" :min="1" :max="65535" :step="1"/>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="魔防">
                <el-slider v-model="options.form.magDefense" :min="1" :max="65535" :step="1"/>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row>
            <el-col :span="12">
              <el-form-item label="跳跃" style="margin-right: 10px">
                <el-slider v-model="options.form.jump" :min="1" :max="65535" :step="1"/>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="硬直">
                <el-slider v-model="options.form.hitRecovery" :min="1" :max="65535" :step="1"/>
              </el-form-item>
            </el-col>
          </el-row>
        </div>
      </div>

      <div class="form-classify">
        <div class="form-classify-title">
          角色三速属性
        </div>
        <div class="form-classify-content">
          <el-row>
            <el-col :span="8">
              <el-form-item label="移速" style="margin-right: 10px">
                <el-slider v-model="options.form.moveSpeed" :min="1" :max="65535" :step="1"/>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="攻速" style="margin-right: 10px">
                <el-slider v-model="options.form.attackSpeed" :min="1" :max="65535" :step="1"/>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="施速">
                <el-slider v-model="options.form.castSpeed" :min="1" :max="65535" :step="1"/>
              </el-form-item>
            </el-col>
          </el-row>
        </div>
      </div>


    </el-form>

    <template #footer>
      <el-button @click="options.show = false">取消</el-button>
      <el-button type="primary" @click="save">确定</el-button>
    </template>
  </el-dialog>
</div>
</template>

<style scoped lang="less">
.form-classify{
  padding: 20px 10px 0 10px;
  border: solid #CCC 1px;
  position: relative;
  margin-bottom: 20px;
  .form-classify-title{
    background: white;
    position: absolute;
    top: -10px;
  }
}
</style>