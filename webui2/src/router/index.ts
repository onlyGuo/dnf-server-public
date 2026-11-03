import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

// 定义路由记录
const routes: Array<RouteRecordRaw> = [
    // 1. 登录页面 (独立页面，不需要布局容器)
    {
        path: '/login',
        name: 'Login',
        component: () => import('../views/Login.vue'),
        meta: {
            title: '登录',
            requiresAuth: false
        }
    },

    // 2. 公共页面区域 (包含公共子页面)
    {
        path: '/',
        name: 'PublicLayout',
        component: () => import('../views/custom/Index.vue'),
        redirect: '/home',
        children: [
            {
                path: 'home',
                name: 'PublicHome',
                component: () => import('../views/custom/legacy/HomePage.vue'),
                meta: { title: '首页', requiresAuth: false }
            },
            {
                path: 'about-clan',
                name: 'AboutClan',
                component: () => import('../views/custom/legacy/AboutClanPage.vue'),
                meta: { title: '模型补丁', requiresAuth: false }
            },
            {
                path: 'about-me',
                name: 'AboutMe',
                component: () => import('../views/custom/legacy/AboutMePage.vue'),
                meta: { title: '地图攻略', requiresAuth: false }
            },
            {
                path: 'faq',
                name: 'Faq',
                component: () => import('../views/custom/legacy/FaqPage.vue'),
                meta: { title: 'FAQ', requiresAuth: false }
            },
            {
                path: 'gallery',
                name: 'Gallery',
                component: () => import('../views/custom/legacy/GalleryPage.vue'),
                meta: { title: '图库', requiresAuth: false }
            },
            {
                path: 'matches',
                name: 'Matches',
                component: () => import('../views/custom/legacy/MatchesPage.vue'),
                meta: { title: '比赛', requiresAuth: false }
            },
            {
                path: 'matches-single',
                name: 'MatchSingle',
                component: () => import('../views/custom/legacy/MatchSinglePage.vue'),
                meta: { title: '比赛详情', requiresAuth: false }
            },
            {
                path: 'players',
                name: 'Players',
                component: () => import('../views/custom/legacy/PlayersPage.vue'),
                meta: { title: '玩家', requiresAuth: false }
            },
            {
                path: 'blog',
                name: 'Blog',
                component: () => import('../views/custom/legacy/BlogPage.vue'),
                meta: { title: '博客', requiresAuth: false }
            },
            {
                path: 'blog-single',
                name: 'BlogSingle',
                component: () => import('../views/custom/legacy/BlogSinglePage.vue'),
                meta: { title: '博客详情', requiresAuth: false }
            },
            {
                path: 'contact',
                name: 'Contact',
                component: () => import('../views/custom/legacy/ContactPage.vue'),
                meta: { title: '联系', requiresAuth: false }
            },
            {
                path: 'index-2',
                name: 'Index2',
                component: () => import('../views/custom/legacy/Index2Page.vue'),
                meta: { title: '首页', requiresAuth: false }
            },
            {
                path: 'notice',
                name: 'Notice',
                component: () => import('../views/custom/legacy/NoticePage.vue'),
                meta: { title: '公告', requiresAuth: false }
            }
        ]
    },

    // 3. 后台管理区域 (包含后台子页面)
    {
        path: '/admin',
        name: 'AdminLayout',
        component: () => import('../views/admin/Index.vue'),
        meta: {
            title: '后台管理',
            requiresAuth: true // 标记：需要登录才能访问
        },
        redirect: '/admin/dashboard',
        children: [
            {
                path: 'dashboard', // 访问地址: /admin/dashboard
                name: 'AdminDashboard',
                component: () => import('../views/admin/Dashboard.vue'),
                meta: {
                    title: '仪表盘'
                }
            },
            {
                path: 'player',
                meta: {
                    title: '玩家管理'
                },
                children: [
                    {
                        path: 'accounts',
                        name: 'PlayerAccounts',
                        component: () => import('../views/admin/player/PlayerAccounts.vue'),
                        meta: {
                            title: '玩家账号管理'
                        }
                    },
                    {
                        path: 'roles',
                        name: 'PlayerRoles',
                        component: () => import('../views/admin/player/PlayerRoles.vue'),
                        meta: {
                            title: '玩家角色管理'
                        }
                    }
                ]
            },
            {
                path: 'pvf',
                meta: {
                    title: 'PVF管理'
                },
                children: [
                    {
                        path: 'manager',
                        name: 'PvfManager',
                        component: () => import('../views/admin/pvf/PvfManager.vue'),
                        meta: {
                            title: 'PVF管理'
                        }
                    }
                ]
            },
            {
                path: 'login',
                meta: {
                    title: '登录器管理'
                },
                children: [
                    {
                        path: 'config',
                        name: 'LauncherConfig',
                        component: () => import('../views/admin/login/LauncherConfig.vue'),
                        meta: {
                            title: '登录器配置'
                        }
                    },
                    {
                        path: 'versions',
                        name: 'LauncherVersions',
                        component: () => import('../views/admin/login/LauncherVersions.vue'),
                        meta: {
                            title: '版本管理'
                        }
                    },
                    {
                        path: 'keys',
                        name: 'GameKeys',
                        component: () => import('../views/admin/login/GameKeys.vue'),
                        meta: { title: '密钥管理' }
                    }
                ]
            },
            {
                path: 'channels',
                name: 'ChannelManager',
                component: () => import('../views/admin/channel/ChannelManager.vue'),
                meta: { title: '频道管理' }
            },
            {
                path: 'database',
                name: 'DatabaseManager',
                component: () => import('../views/admin/database/DatabaseManager.vue'),
                meta: { title: '数据库管理' }
            },
            {
                path: 'assist',
                name: 'AssistManager',
                component: () => import('../views/admin/assist/AssistManager.vue'),
                meta: {
                    title: '内辅管理'
                }
            }
        ]
    },

    // 4. 404 页面 (捕获所有未定义的路由)
    {
        path: '/:pathMatch(.*)*',
        name: 'NotFound',
        // 这里简单演示，你可以指向一个专门的 404 组件
        redirect: '/'
    }
]

// 创建路由实例
const router = createRouter({
    // 使用 HTML5 History 模式
    history: createWebHistory(),
    routes
})

// 全局前置守卫 (简单的权限控制)
router.beforeEach((to) => {
    // 1. 设置标题
    const pageTitle = to.meta.title as string | undefined
    document.title = pageTitle ? `${pageTitle} - DNF 服务器管理平台` : 'DNF 服务器管理平台'

    const isAuthenticated = !!localStorage.getItem('token')

    // 2. 鉴权逻辑
    if (to.meta.requiresAuth && !isAuthenticated) {
        // 返回要跳转的路径对象，相当于 next(...)
        return {
            path: '/login',
            query: { redirect: to.fullPath }
        }
    }
})

export default router
