import axios from "axios";
import router from "../router/index.js";
import {ElMessage} from "element-plus";

axios.defaults.timeout = 10000
axios.defaults.headers.post['Content-Type'] = 'application/json;charset=UTF-8'
axios.defaults.baseURL = 'api/v1'
axios.interceptors.request.use(
    config => {
        // 允许携带cookie
        config.withCredentials = true;
        config.headers['Content-Type'] = "application/json";
        config.headers['token'] = window.localStorage.getItem("token");
        return config
    },
    error => {
        return Promise.error(error)
    }
)

/**
 * 相应拦截器
 */
axios.interceptors.response.use(
    response => {
        if (response.status === 200) {
            return Promise.resolve(response)
        } else {
            return Promise.reject(response)
        }
    },
    // 服务器状态码不是200的情况
    error => {
        console.log(error)
        try {
            if (error.response.status) {
                if (error.response.status === 401) {
                    router.replace({
                        path: '/login'
                    })
                } else if (error.response.status === 400) {
                    ElMessage.error(error.response.data.message);
                } else {
                    ElMessage.error('网络错误，请稍后再试');
                }
                return Promise.reject(error.response)
            }
        }catch (e){
            ElMessage.error('网络错误，请稍后再试');
            return Promise.reject(error.response)
        }
    }
)

export default axios;