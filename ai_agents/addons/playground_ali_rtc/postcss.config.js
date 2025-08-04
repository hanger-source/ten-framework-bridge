/** @type {import('postcss-load-config').Config} */
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
    "@minko-fe/postcss-pxtoviewport": {
      viewportWidth: 375,
      exclude: /node_modules/,
      include: /\/src\/platform\/mobile\//,
    },
  },
};
