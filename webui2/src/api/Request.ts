import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import ArcoVue from '@arco-design/web-vue';
import '@arco-design/web-vue/dist/arco.css';
import router from "../router";

// 1. 定义后端返回的标准数据格式
// 根据你们后端的实际情况修改，通常是 code, data, msg
interface Result<T = any> {
    code: number
    msg: string
    data: T
}

class Request {
    private instance: AxiosInstance
    private baseConfig: AxiosRequestConfig = {
        // 默认基础路径，读取 .env 文件中的 VITE_API_URL
        baseURL: import.meta.env.VITE_API_URL || '/',
        timeout: 10000,
        withCredentials: false
    }

    constructor(config: AxiosRequestConfig) {
        // 使用默认配置覆盖传入的配置
        this.instance = axios.create(Object.assign(this.baseConfig, config))

        // 初始化拦截器
        this.setupInterceptors()
    }

    // 配置拦截器
    private setupInterceptors() {
        // A. 请求拦截器
        this.instance.interceptors.request.use(
            (config: InternalAxiosRequestConfig) => {
                // 从 LocalStorage 或 Pinia 获取 Token
                const token = localStorage.getItem('token')
                if (token) {
                    // 按标准 Bearer 格式添加到 Header
                    config.headers.Authorization = `Bearer ${token}`
                }
                return config
            },
            (error) => {
                return Promise.reject(error)
            }
        )

        // B. 响应拦截器
        this.instance.interceptors.response.use(
            (response: AxiosResponse) => {
                const { message } = response.data
                // 假设后端约定 code === 200 代表成功
                if (response.status === 200) {
                    return response
                } else if(response.status === 401){
                    localStorage.removeItem('token')
                    router.replace('/login')
                    return Promise.reject(new Error('Unauthorized'))
                }else{
                    // 业务逻辑错误（如密码错误），弹出提示
                    ArcoVue.Message.error(message || '系统未知错误')
                    return Promise.reject(new Error(message || 'Error'))
                }
            },
            (error) => {
                // 处理 HTTP 状态码错误
                let msg = ''
                const status = error.response?.status

                switch (status) {
                    case 401:
                        msg = '登录过期，请重新登录'
                        // 清除 Token 并跳转登录页
                        localStorage.removeItem('token')
                        router.replace('/login')
                        break
                    case 403:
                        msg = error.response?.data?.message || '拒绝访问 (403)'
                        break
                    case 404:
                        msg = '请求资源不存在 (404)'
                        break
                    case 413:
                        msg = error.response?.data?.message || '文件超过服务器允许的上传大小 (413)'
                        break
                    case 500:
                        msg = error.response?.data?.message || error.message || '服务器内部错误 (500)'
                        break
                    default:
                        msg = error.response?.data?.message || error.message || '网络连接故障'
                }
                ArcoVue.Message.error(msg)
                return Promise.reject(error)
            }
        )
    }

    // C. 常用方法封装
    // T 是返回的数据结构中 data 字段的类型
    public get<T = any>(url: string, config?: AxiosRequestConfig): Promise<Result<T>> {
        return this.instance.get(url, config)
    }

    public post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<Result<T>> {
        return this.instance.post(url, data, config)
    }

    public put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<Result<T>> {
        return this.instance.put(url, data, config)
    }

    public delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<Result<T>> {
        return this.instance.delete(url, config)
    }
}

// 导出单例，方便直接调用
export default new Request({})
