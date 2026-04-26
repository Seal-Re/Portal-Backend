// vue.config.js
const BACKEND_URL = process.env.VUE_APP_BACKEND_URL || 'http://192.168.1.97:43999';

module.exports = {
  devServer: {
    proxy: {
      '/upload': {
        target: BACKEND_URL,
        changeOrigin: true,
        pathRewrite: {
          '^/upload': '/portal/upload'
        }
      },
      '/docker': {
        target: BACKEND_URL,
        changeOrigin: true,
        pathRewrite: {
          '^/docker': '/portal/docker'
        }
      }
    }
  }
}
