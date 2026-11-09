import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const basePath = process.env.VITE_BASE_PATH ?? "/";
const basePrefix = basePath === "/" ? "" : basePath.replace(/\/$/, "");

export default defineConfig({
  base: basePath,
  plugins: [react()],
  server: {
    proxy: {
      [`${basePrefix}/api`]: {
        target: "http://localhost:8080",
        rewrite: (path) => path.replace(basePrefix, ""),
      },
    },
  },
});
