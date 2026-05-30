import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'

const client = axios.create({
  baseURL: '/api',
  timeout: 10000
})

client.interceptors.response.use(
  res => res.data,
  err => {
    const msg = err.response?.data?.error || err.message || '请求失败'
    return Promise.reject(new Error(msg))
  }
)

const api = {
  get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return client.get<T, T>(url, config)
  },
  post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return client.post<T, T>(url, data, config)
  },
  put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return client.put<T, T>(url, data, config)
  },
  patch<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return client.patch<T, T>(url, data, config)
  },
  delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return client.delete<T, T>(url, config)
  }
}

export default api
