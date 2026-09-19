import api from './axios'

export const ebookApi = {
  list: (params?: { page?: number; size?: number }) =>
    api.get('/ebooks', { params }),

  listMine: () => api.get('/ebooks/mine/list'),

  getById: (id: string) => api.get(`/ebooks/${id}`),

  /** 试读页内容 */
  getSample: (id: string) => api.get(`/ebooks/${id}/sample`),

  /** 阅读器会话：试读边界、是否已购、续读页码均由服务端给出 */
  openReader: (id: string) => api.get(`/ebooks/${id}/reader`),

  /** 分页取正文，越界页由服务端拒绝 */
  getPage: (id: string, page: number) => api.get(`/ebooks/${id}/pages/${page}`),

  create: (data: any) => api.post('/ebooks', data),

  /** 作者下架 */
  offline: (id: string) => api.post(`/ebooks/${id}/offline`),
}
