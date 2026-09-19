import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Card,
  Typography,
  Button,
  Descriptions,
  Tag,
  Avatar,
  message,
  Spin,
} from 'antd'
import { ebookApi } from '../api/ebook'
import { orderApi } from '../api/order'
import type { Ebook, ReaderSession } from '../types'

const { Title, Paragraph } = Typography

function EbookDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [ebook, setEbook] = useState<Ebook | null>(null)
  const [session, setSession] = useState<ReaderSession | null>(null)
  const [loading, setLoading] = useState(false)
  const [paying, setPaying] = useState(false)

  const loadEbookDetail = async () => {
    if (!id) return
    setLoading(true)
    try {
      const res = await ebookApi.getById(id)
      setEbook(res.data?.data || res.data)
    } catch (error) {
      console.error('Failed to load ebook:', error)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (id) {
      loadEbookDetail()
      // 登录用户拉取授权状态；未登录走匿名试读，失败忽略
      ebookApi
        .openReader(id)
        .then((res) => {
          const body = res.data
          if (body?.success !== false) {
            setSession(body?.data ?? body)
          }
        })
        .catch(() => {})
    }
  }, [id])

  const handlePurchaseAndPay = async () => {
    if (!id) return
    setPaying(true)
    try {
      const orderRes = await orderApi.createEbookOrder(id)
      const order = orderRes.data?.data || orderRes.data
      const payRes = await orderApi.pay(order.id, 'SUCCESS')
      const payBody = payRes.data
      if (payBody && payBody.success === false) {
        message.error(payBody.message || '支付失败')
        return
      }
      const paid = payBody?.data || payBody
      if (paid.status !== 'PAID') {
        message.error('支付未完成')
        return
      }
      message.success('支付成功，已解锁全文')
      setSession((prev) => (prev ? { ...prev, purchased: true } : prev))
      navigate(`/ebooks/read/${id}`)
    } catch (error: any) {
      message.error(error?.response?.data?.message || '支付失败，请稍后重试')
    } finally {
      setPaying(false)
    }
  }

  if (loading || !ebook) {
    return <Spin style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />
  }

  const offShelf = ebook.status === 'OFFLINE'
  const purchased = !!session?.purchased

  return (
    <div>
      <Card>
        <div style={{ display: 'flex', gap: 24 }}>
          <div
            style={{
              width: 200,
              height: 280,
              background: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
              borderRadius: 8,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#fff',
              fontSize: 96,
              flexShrink: 0,
            }}
          >
            📖
          </div>
          <div style={{ flex: 1 }}>
            <Title level={2}>
              {ebook.title}{' '}
              {offShelf && <Tag color="red">已下架</Tag>}
              {purchased && <Tag color="green">已购买</Tag>}
            </Title>
            <div style={{ marginBottom: 16 }}>
              <Avatar icon={<span>👤</span>} src={ebook.creator?.avatar} />
              <span style={{ marginLeft: 8 }}>{ebook.creator?.username}</span>
              <Tag color="green" style={{ marginLeft: 8 }}>
                {ebook.fileType}
              </Tag>
            </div>
            <Paragraph type="secondary">{ebook.description}</Paragraph>
            <Descriptions column={2} style={{ marginTop: 16 }}>
              {ebook.pageCount != null && (
                <Descriptions.Item label="全书页数">{ebook.pageCount} 页</Descriptions.Item>
              )}
              {ebook.wordCount != null && (
                <Descriptions.Item label="全书字数">{ebook.wordCount} 字</Descriptions.Item>
              )}
              <Descriptions.Item label="试读范围">
                前 {session ? session.sampleEndPage : Math.max(1, Math.floor((ebook.pageCount || 0) * 0.1))} 页（全书实际内容的 {ebook.sampleEndPercent * 100}%）
              </Descriptions.Item>
              <Descriptions.Item label="价格" className="price-text">
                ¥{ebook.price}
              </Descriptions.Item>
            </Descriptions>
            <div style={{ marginTop: 24, gap: 12, display: 'flex' }}>
              {purchased ? (
                <Button
                  type="primary"
                  size="large"
                  onClick={() => navigate(`/ebooks/read/${ebook.id}`)}
                >
                  继续阅读全文
                </Button>
              ) : offShelf ? (
                <Button type="primary" size="large" disabled>
                  已下架，无法购买
                </Button>
              ) : (
                <Button
                  type="primary"
                  size="large"
                  onClick={handlePurchaseAndPay}
                  loading={paying}
                >
                  立即购买并解锁
                </Button>
              )}
              <Button
                size="large"
                onClick={() => navigate(`/ebooks/read/${ebook.id}`)}
                disabled={offShelf && !purchased}
              >
                {purchased ? '阅读' : '免费试读'}
              </Button>
            </div>
            {offShelf && purchased && (
              <Paragraph type="secondary" style={{ marginTop: 16 }}>
                本书已下架，您之前的购买仍然有效，可继续阅读全文。
              </Paragraph>
            )}
          </div>
        </div>
      </Card>
    </div>
  )
}

export default EbookDetail
