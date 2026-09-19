import api from './axios'

export const orderApi = {
  list: (params?: { page?: number; size?: number }) =>
    api.get('/orders', { params }),

  getById: (id: string) => api.get(`/orders/${id}`),

  /** 电子书下单（幂等：重复/并发只保留一条有效订单） */
  createEbookOrder: (ebookId: string) =>
    api.post(`/orders/ebooks/${ebookId}`),

  /**
   * 支付确认。result 缺省为成功；沙箱演示可传 FAIL 验证失败不留已购状态。
   */
  pay: (orderId: string, result?: 'SUCCESS' | 'FAIL') =>
    api.post(`/orders/${orderId}/pay`, result ? { result } : {}),

  requestInvoice: (orderId: string, data: any) =>
    api.post(`/orders/${orderId}/invoice`, data),
}
