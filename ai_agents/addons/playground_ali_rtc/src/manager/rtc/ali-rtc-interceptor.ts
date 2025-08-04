// 拦截 Ali RTC 的日志上报请求，避免 ERR_BLOCKED_BY_CLIENT 错误
export class AliRtcInterceptor {
    static interceptLogRequests() {
        // 保存原始的 fetch 函数
        const originalFetch = window.fetch;

        // 重写 fetch 函数
        window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
            const url = typeof input === 'string' ? input : input.toString();

            // 检查是否是 Ali RTC 的日志上报请求
            if (url.includes('onertc-client-zb.cn-zhangjiakou.log.aliyuncs.com') ||
                url.includes('logstores') ||
                url.includes('aliyuncs.com/logstores')) {

                console.log('拦截 Ali RTC 日志上报请求:', url);

                // 返回一个成功的响应，避免错误
                return new Response('{"success": true}', {
                    status: 200,
                    headers: { 'Content-Type': 'application/json' }
                });
            }

            // 对于其他请求，使用原始的 fetch
            return originalFetch(input, init);
        };
    }
}