import { createRouter, createWebHistory, RouteRecordRaw } from "vue-router";

export const Layout = () => import("@/layouts/index.vue");
const SiteRedirect = () => import("@/views/redirect/site.vue");

// 静态路由
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: "/redirect",
    component: Layout,
    meta: { hidden: true },
    children: [
      {
        path: "/redirect/:path(.*)",
        component: () => import("@/views/redirect/index.vue"),
        meta: { hidden: true }
      }
    ]
  },
  {
    path: "/auth/:pathMatch(.*)*",
    component: SiteRedirect,
    meta: { hidden: true },
  },
  {
    path: "/:pathMatch(.*)*",
    component: SiteRedirect,
    meta: { hidden: true },
  },
  {
    path: "/",
    name: "/",
    component: Layout,
    redirect: "/dashboard",
    children: [
      {
        path: "dashboard",
        component: () => import("@/views/dashboard/index.vue"),
        name: "Dashboard",
        meta: {
          title: "仪表盘",
          icon: "Orange",
          affix: true,
          keepAlive: true,
          alwaysShow: false,
        },
      },
    ],
  },
  {
    path: "/ai",
    component: Layout,
    redirect: "/ai/conversation",
    meta: {
      title: "AI 对话",
      icon: "ChatDotRound",
      alwaysShow: true,
    },
    children: [
      {
        path: "conversation",
        component: () => import("@/views/ai/conversation/index.vue"),
        name: "AiConversation",
        meta: {
          title: "对话管理",
          icon: "ChatDotRound",
          keepAlive: true,
        },
      },
      {
        path: "quota",
        component: () => import("@/views/ai/quota/index.vue"),
        name: "AiQuota",
        meta: {
          title: "额度管理",
          icon: "Coin",
          keepAlive: true,
        },
      },
      {
        path: "quota-log",
        component: () => import("@/views/ai/quota-log/index.vue"),
        name: "AiQuotaLog",
        meta: {
          title: "额度流水",
          icon: "Tickets",
          keepAlive: true,
        },
      },
      {
        path: "rag",
        component: () => import("@/views/ai/rag/index.vue"),
        name: "AiRag",
        meta: {
          title: "RAG 控制",
          icon: "Connection",
          keepAlive: true,
        },
      },
    ],
  }
];

/**
 * 创建路由
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: constantRoutes,
  // 刷新时，滚动条位置还原
  scrollBehavior: () => ({ left: 0, top: 0 }),
});
export default router;
