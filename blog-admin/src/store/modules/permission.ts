import type { RouteRecordRaw } from "vue-router";
import { constantRoutes } from "@/router";
import { getRouters } from "@/api/system/auth";
import { defineStore } from "pinia";
import { ref } from "vue";
const modules = import.meta.glob("../../views/**/**.vue");
import ParentView from '@/components/ParentView/index.vue'

const Layout = () => import("@/layouts/index.vue");

type BackendRoute = {
  path: string;
  name?: string;
  redirect?: string;
  meta?: RouteRecordRaw["meta"];
  component?: string;
  children?: BackendRoute[];
}

/**
 * 递归过滤有权限的异步(动态)路由
 */
const filterAsyncRoutes = (routes: BackendRoute[]) => {
  const asyncRoutes: RouteRecordRaw[] = [];

  routes.forEach((route) => {
    const { component: routeComponent, children, ...routeRecord } = route
    const tmpRoute = { ...routeRecord } as RouteRecordRaw

    if (routeComponent) {
      if (routeComponent === "Layout") {
        tmpRoute.component = Layout;
      } else if (routeComponent === 'ParentView') {
        tmpRoute.component = ParentView
      } else {
        {
          const component = modules[`../../views${routeComponent}.vue`];
          if (component) {
            tmpRoute.component = component;
          } else {
            return;
          }
        }
      }
    }

    if (children?.length) {
      tmpRoute.children = filterAsyncRoutes(children);
    }

    asyncRoutes.push(tmpRoute);

  });

  return asyncRoutes;
};

// setup
export const usePermissionStore = defineStore("permission", () => {
  // state
  const routes = ref<RouteRecordRaw[]>([]);

  // actions
  function setRoutes(newRoutes: RouteRecordRaw[]) {
    routes.value = constantRoutes.concat(newRoutes);
  }
  /**
   * 生成动态路由
   *
   * @returns
   */
  function generateRoutes() {
    return new Promise<RouteRecordRaw[]>((resolve, reject) => {
      // 接口获取所有路由
      getRouters()
        .then(({ data: asyncRoutes }) => {
          // 根据角色获取有访问权限的路由
          const accessedRoutes = filterAsyncRoutes(asyncRoutes as BackendRoute[]);
          setRoutes(accessedRoutes);
          resolve(accessedRoutes);
        })
        .catch((error) => {
          reject(error);
        });
    });
  }
  return {
    routes,
    generateRoutes,
  };
});
