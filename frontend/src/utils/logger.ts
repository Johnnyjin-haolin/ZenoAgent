/**
 * 统一日志工具类
 * 根据环境变量控制日志输出级别
 * 
 * 使用方式：
 * import logger from '@/utils/logger';
 * logger.debug('调试信息');
 * logger.info('普通信息');
 * logger.warn('警告信息');
 * logger.error('错误信息');
 */

type LogLevel = 'debug' | 'info' | 'warn' | 'error' | 'none';

class Logger {
  private level: LogLevel;

  constructor() {
    // 从环境变量获取日志级别，默认为 'info'
    // 开发环境：debug，生产环境：error
    const envLevel = import.meta.env.VITE_LOG_LEVEL as LogLevel;
    const isDev = import.meta.env.DEV;
    
    if (envLevel) {
      this.level = envLevel;
    } else {
      // 根据环境自动设置
      this.level = isDev ? 'debug' : 'error';
    }
  }

  /**
   * 检查是否应该输出日志
   */
  private shouldLog(level: LogLevel): boolean {
    const levels: LogLevel[] = ['debug', 'info', 'warn', 'error', 'none'];
    const currentIndex = levels.indexOf(this.level);
    const targetIndex = levels.indexOf(level);
    
    if (currentIndex === -1 || targetIndex === -1) {
      return false;
    }
    
    // none 级别不输出任何日志
    if (this.level === 'none') {
      return false;
    }
    
    // 只有目标级别 >= 当前级别时才输出
    return targetIndex >= currentIndex;
  }

  /**
   * 调试日志（开发环境使用）
   */
  debug(...args: any[]): void {
    if (this.shouldLog('debug')) {
      console.log('[DEBUG]', ...args);
    }
  }

  /**
   * 普通信息日志
   */
  info(...args: any[]): void {
    if (this.shouldLog('info')) {
      console.info('[INFO]', ...args);
    }
  }

  /**
   * 警告日志
   */
  warn(...args: any[]): void {
    if (this.shouldLog('warn')) {
      console.warn('[WARN]', ...args);
    }
  }

  /**
   * 错误日志（生产环境也会输出）
   */
  error(...args: any[]): void {
    if (this.shouldLog('error')) {
      console.error('[ERROR]', ...args);
    }
  }
}

// 导出单例
const logger = new Logger();
export default logger;
