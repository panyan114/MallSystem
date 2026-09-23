import service from '@/api/axios'

export function get<T>(url: string, params?: any): Promise<T> {
  return service.get(url, { params })
}

export function post<T>(url: string, data?: any): Promise<T> {
  return service.post(url, data)
}

export function put<T>(url: string, data?: any): Promise<T> {
  return service.put(url, data)
}

export function del<T>(url: string): Promise<T> {
  return service.delete(url)
}
