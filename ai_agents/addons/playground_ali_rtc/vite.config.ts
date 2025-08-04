import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import svgr from 'vite-plugin-svgr'
import path from 'path'

// https://vitejs.dev/config/
export default defineConfig({
    plugins: [react(), svgr()],
    resolve: {
        alias: {
            '@': path.resolve(__dirname, './src'),
        },
    },
    server: {
        port: 4000,
        proxy: {
            '/api/agents': {
                target: process.env.AGENT_SERVER_URL || 'http://localhost:7070',
                changeOrigin: true,
                rewrite: (path) => path.replace(/^\/api\/agents/, ''),
            },
            '/api/vector': {
                target: process.env.AGENT_SERVER_URL || 'http://localhost:7070',
                changeOrigin: true,
                rewrite: (path) => path.replace(/^\/api\/vector/, '/vector'),
            },
            '/api/token': {
                target: process.env.AGENT_SERVER_URL || 'http://localhost:7070',
                changeOrigin: true,
                rewrite: (path) => path.replace(/^\/api\/token/, '/token'),
            },
            '/api/dev/v1/addons/default-properties': {
                target: process.env.AGENT_SERVER_URL || 'http://localhost:7070',
                changeOrigin: true,
                rewrite: (path) => path.replace(/^\/api\/dev\/v1\/addons\/default-properties/, '/dev-tmp/addons/default-properties'),
            },
            '/api/dev': {
                target: process.env.TEN_DEV_SERVER_URL || 'http://localhost:7070',
                changeOrigin: true,
                rewrite: (path) => path.replace(/^\/api\/dev/, '/api/designer'),
            },
        },
    },
    build: {
        outDir: 'dist',
        sourcemap: true,
    },
})