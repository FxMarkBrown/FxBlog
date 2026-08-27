import { config } from 'md-editor-v3'
import { installMarkdownAlignPlugin } from '@/utils/markdownAlign'

export default defineNuxtPlugin(() => {
  config({
    markdownItConfig(md) {
      installMarkdownAlignPlugin(md)
    },
    markdownItPlugins(plugins) {
      return plugins.map((plugin) => {
        if (plugin.type !== 'xss') {
          return plugin
        }

        return {
          ...plugin,
          options: {
            ...(plugin.options || {}),
            extendedWhiteList: {
              div: ['class'],
              p: ['class'],
              span: ['class']
            }
          }
        }
      })
    }
  })
})
