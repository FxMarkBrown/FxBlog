// @ts-check
import prettier from 'eslint-config-prettier'
import withNuxt from './.nuxt/eslint.config.mjs'

export default withNuxt(
  {
    ignores: [
      'node_modules',
      '.nuxt',
      '.output',
      'dist',
      // 以下为 unplugin-auto-import / unplugin-vue-components 自动生成的声明文件
      'src/components.d.ts',
      'src/auto-imports.d.ts',
      // 字体资源目录（含构建产物）
      'src/assets/font/**'
    ]
  },
  {
    // 项目页面/布局统一采用「目录 + index.vue」的命名约定（Nuxt 风格路由组件），
    // 单词组件名是既定目录结构而非语义命名，重命名成本高且无收益，故对这些目录关闭该规则
    files: ['src/views/**', 'src/pages/**', 'src/layouts/**', 'src/layout/**'],
    rules: {
      'vue/multi-word-component-names': 'off'
    }
  },
  {
    rules: {
      // 站点核心功能是基于 marked/highlight.js 渲染后端下发的 Markdown/HTML 内容
      // （公告、评论、文章、动态等 13 处），v-html 是既定架构而非偶发疏漏，故关闭该规则
      'vue/no-v-html': 'off'
    }
  },
  // 关闭所有与 Prettier 冲突的格式类规则，格式化统一交给 Prettier
  prettier
)
