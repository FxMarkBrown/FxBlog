// @ts-check
import js from '@eslint/js'
import globals from 'globals'
import tseslint from 'typescript-eslint'
import pluginVue from 'eslint-plugin-vue'
import prettier from 'eslint-config-prettier'

export default tseslint.config(
  {
    ignores: [
      'node_modules',
      'dist',
      // 以下为 unplugin-auto-import / unplugin-vue-components 自动生成的声明文件
      'src/auto-imports.d.ts',
      'src/components.d.ts'
    ]
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    languageOptions: {
      globals: {
        ...globals.browser,
        ...globals.node
      }
    }
  },
  {
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: {
        // Vue SFC 内的 <script> 交给 typescript-eslint 解析，保证 TS 语法可用
        parser: tseslint.parser
      }
    }
  },
  {
    files: ['**/*.{ts,tsx,vue}'],
    rules: {
      // TS 文件的未定义标识符由 TypeScript 编译器负责检查（vue-tsc），
      // 且项目通过 unplugin-auto-import 自动注入 ref/reactive 等全局 API，
      // eslint 的 no-undef 在此场景下只会产生误报，故对 TS/Vue 文件关闭
      'no-undef': 'off'
    }
  },
  {
    // 项目页面/布局统一采用「目录 + index.vue」的命名约定（Nuxt 风格路由组件），
    // 单词组件名是既定目录结构而非语义命名，重命名成本高且无收益，故对这些目录关闭该规则
    files: ['src/views/**', 'src/layouts/**', 'src/components/**'],
    rules: {
      'vue/multi-word-component-names': 'off'
    }
  },
  {
    rules: {
      // 后台核心功能之一是基于 marked/md-editor-v3 渲染后端下发的 Markdown/HTML 内容
      // （文章预览、评论、动态等），v-html 是既定架构而非偶发疏漏，故关闭该规则
      'vue/no-v-html': 'off',
      // 后台管理端大量使用 Element Plus 表单/表格承接后端动态下发的 JSON 数据，
      // 请求响应普遍缺少精确类型建模，any 是既定写法而非疏漏，全量建模成本高且无收益，故关闭
      '@typescript-eslint/no-explicit-any': 'off',
      // 代码中大量空 catch 块是刻意的「静默降级」容错写法（如刷新失败不打扰用户），
      // 逐块添加无意义注释只会制造噪音，故允许空 catch（空 if/for 等仍报错）
      'no-empty': ['error', { allowEmptyCatch: true }],
      // catch (error) 后不使用 error 与空 catch 同理，属于刻意吞错的既定写法；
      // 未使用的变量/参数允许以下划线开头显式标记为有意忽略
      '@typescript-eslint/no-unused-vars': [
        'error',
        { caughtErrors: 'none', argsIgnorePattern: '^_', varsIgnorePattern: '^_' }
      ]
    }
  },
  // 关闭所有与 Prettier 冲突的格式类规则，格式化统一交给 Prettier
  prettier
)
