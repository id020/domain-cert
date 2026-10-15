/// <reference types="vite/client" />

/** 告知 TypeScript 将 Vue 单文件组件视作可导入的组件模块。 */
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}
