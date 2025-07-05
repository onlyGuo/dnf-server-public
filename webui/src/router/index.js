import {createRouter, createWebHashHistory} from 'vue-router'
import Home from '../views/Home.vue'

const routes = [
    {
        path: '/',
        name: 'Home',
        component: Home
    },
    {
        path: '/login',
        component: () => import('../views/Login.vue'),
    },
    {
        path: '/register',
        component: () => import('../views/Register.vue'),
    },
    {
        path: '/recharge',
        component: () => import('../views/Recharge.vue'),
    },
    {
        path: '/manager',
        component: () => import('../views/Manager.vue'),
        redirect: '/manager/account',
        children: [
            {
                path: '/manager/account',
                component: () => import('../views/manager/MgrAccount.vue'),
            }, {
                path: '/manager/charac',
                component: () => import('../views/manager/MgrCharach.vue'),
            }, {
                path: '/manager/notice',
                component: () => import('../views/manager/MgrNotice.vue'),
            }, {
                path: '/manager/news',
                component: () => import('../views/manager/MgrNews.vue'),
            }, {
                path: '/manager/action',
                component: () => import('../views/manager/MgrAction.vue'),
            }, {
                path: '/manager/keys',
                component: () => import('../views/manager/MgrKeys.vue'),
            }, {
                path: '/manager/k-logs',
                component: () => import('../views/manager/MgrKLogs.vue'),
            }, {
                path: '/manager/databases',
                component: () => import('../views/manager/Databases.vue'),
            }
        ]
    }
]

export const router = createRouter({
    history: createWebHashHistory(),
    routes,
})

export default router
