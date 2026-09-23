import service from '@/api/axios'

// `api/axios.ts` 的响应拦截器已经把 AxiosResponse 拆成了后端信封 `{code,message,data}`，
// 所以运行时拿到的**不是** AxiosResponse，但 axios 的类型签名仍然说是。
// 这个断言是在描述既成事实，不是在掩盖类型错误。T 由调用方声明成信封类型。
//
// 注意：本文件目前全项目零引用（各视图都是直接 import '@/api/xxxApi'）。

export function get<T>(url: string, params?: any): Promise<T> {
  return service.get(url, { params }) as unknown as Promise<T>
}

export function post<T>(url: string, data?: any): Promise<T> {
  return service.post(url, data) as unknown as Promise<T>
}

export function put<T>(url: string, data?: any): Promise<T> {
  return service.put(url, data) as unknown as Promise<T>
}

export function del<T>(url: string): Promise<T> {
  return service.delete(url) as unknown as Promise<T>
}
