import api from './axios'

export const orderApi = {
  list: () => api.get('/orders'),

  getById: (id: string) => api.get(`/orders/${id}`),

  create: (data: { type: string; itemId: string; plan?: string }) =>
    api.post('/orders', data),

  // 确认支付；fail=true 模拟支付失败（订单保持待支付，不产生已购状态）
  pay: (orderId: string, fail?: boolean) =>
    api.post(`/orders/${orderId}/pay`, { fail: fail ?? false }),

  requestInvoice: (orderId: string, data: any) =>
    api.post(`/orders/${orderId}/invoice`, data),
}
