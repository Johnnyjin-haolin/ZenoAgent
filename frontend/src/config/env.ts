/**
 * 前端运行时常量（由 .env 注入，构建时写入）
 * 变量说明见 frontend 根目录 .env.example，仅 VITE_ 开头会暴露到前端。
 */

/** API 基础地址。未设置时为空（相对路径，与前端同源，由 Nginx/代理转发） */
export const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '';

/** 日志级别：debug | info | warn | error | none。未设置时开发环境为 debug，生产为 error */
export const logLevel = import.meta.env.VITE_LOG_LEVEL as
  | 'debug'
  | 'info'
  | 'warn'
  | 'error'
  | 'none'
  | undefined;

/** 是否为开发环境（Vite DEV） */
export const isDev = import.meta.env.DEV;
