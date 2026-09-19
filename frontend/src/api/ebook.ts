import api from './axios'

export const ebookApi = {
  list: (params?: { page?: number; size?: number }) =>
    api.get('/ebooks', { params }),

  getById: (id: string) => api.get(`/ebooks/${id}`),

  // 阅读入口授权状态（试读边界、总页数、是否已购）
  getAccess: (id: string) => api.get(`/ebooks/${id}/access`),

  // 逐页获取正文，服务端授权，越界返回 locked
  getPage: (id: string, page: number) => api.get(`/ebooks/${id}/pages/${page}`),

  // 创建购买订单（重复/并发调用回读同一订单）
  purchase: (id: string) => api.post(`/ebooks/${id}/purchase`),

  create: (data: any) => api.post('/ebooks', data),

  publish: (id: string) => api.put(`/ebooks/${id}/publish`),

  offline: (id: string) => api.put(`/ebooks/${id}/offline`),
}
