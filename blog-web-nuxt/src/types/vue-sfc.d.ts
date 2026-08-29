// vue-cropper 的 lib/index.ts 以相对路径导入同目录的 .vue 单文件组件，
// 该包未提供对应的类型声明。vue-tsc 只会接管项目内的 .vue 文件，
// 这里用通配声明兜底 node_modules 中无类型的 .vue 导入。
declare module '*.vue' {
  import type { DefineComponent } from 'vue'

  const component: DefineComponent<object, object, unknown>
  export default component
}
