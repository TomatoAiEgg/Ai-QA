import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

function appendUtf8Charset(contentType) {
  if (typeof contentType !== 'string') {
    return contentType
  }

  const normalizedType = contentType.toLowerCase()
  const shouldAppendCharset =
    (normalizedType.startsWith('text/') ||
      normalizedType.includes('javascript') ||
      normalizedType.includes('json') ||
      normalizedType.includes('xml') ||
      normalizedType.includes('svg')) &&
    !normalizedType.includes('charset=')

  return shouldAppendCharset ? `${contentType}; charset=utf-8` : contentType
}

function utf8ResponseHeadersPlugin() {
  const patchResponseHeader = (res) => {
    const originalSetHeader = res.setHeader.bind(res)

    res.setHeader = (name, value) => {
      if (typeof name === 'string' && name.toLowerCase() === 'content-type') {
        return originalSetHeader(name, appendUtf8Charset(value))
      }
      return originalSetHeader(name, value)
    }
  }

  return {
    name: 'utf8-response-headers',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        patchResponseHeader(res)
        next()
      })
    },
    configurePreviewServer(server) {
      server.middlewares.use((req, res, next) => {
        patchResponseHeader(res)
        next()
      })
    }
  }
}

export default defineConfig({
  plugins: [vue(), utf8ResponseHeadersPlugin()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:7000',
        changeOrigin: true
      },
      '/ai': {
        target: 'http://localhost:7000',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    emptyOutDir: true
  }
})
