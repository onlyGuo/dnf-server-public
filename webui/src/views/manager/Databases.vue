<template>
  <div>
    <codemirror v-model="code" :options="options" ref="mycode"/>
    <div style="position: fixed; right: 20px; top: 90px; height: 30px; width: 130px;">
      <el-button size="mini" round @click="excute(0)">查询</el-button>
      <el-button size="mini" round @click="excute(1)">更新</el-button>
    </div>
    <div>
      <el-table
          :data="tableData"
          border
          style="width: 100%">
        <el-table-column v-for="(i, key) in tableData[0]" :key="key"
            :prop="key"
            :label="key">
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script>
import 'codemirror/mode/sql/sql.js'
import 'codemirror/lib/codemirror.css'
// 引入主题后还需要在 options 中指定主题才会生效
import 'codemirror/theme/cobalt.css'
let Base64 = require('js-base64').Base64
export default {
  name: "Databases",
  data(){
    return {
      code: '',
      tableData: [

      ],
      options: {
        tabSize: 4,
        mode: 'text/x-mysql',
        theme: 'cobalt',
        lineNumbers: true,
        line: true,
        extraKeys: {'Ctrl': 'autocomplete'}
      }
    }
  },
  methods: {
    excute(type){
      let req = {
        type: type,
        script: Base64.encode(this.code)
      }
      this.ser.post("database", req).then(res => {
        this.tableData = res.data;
        this.$message.success("执行完毕");
      })
    },
    // utf8Endoce(string){
    //   string = string.replace(/\r\n/g,"\n");
    //   let utftext = "";
    //   for (let n = 0; n < string.length; n++) {
    //     let c = string.charCodeAt(n);
    //     if (c < 128) {
    //       utftext += String.fromCharCode(c);
    //     } else if((c > 127) && (c < 2048)) {
    //       utftext += String.fromCharCode((c >> 6) | 192);
    //       utftext += String.fromCharCode((c & 63) | 128);
    //     } else {
    //       utftext += String.fromCharCode((c >> 12) | 224);
    //       utftext += String.fromCharCode(((c >> 6) & 63) | 128);
    //       utftext += String.fromCharCode((c & 63) | 128);
    //     }
    //   }
    //   return utftext;
    // }
  },

}
</script>

<style scoped>

</style>