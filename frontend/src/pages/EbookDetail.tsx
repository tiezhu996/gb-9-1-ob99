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
  Alert,
} from 'antd'
import { ebookApi } from '../api/ebook'
import { orderApi } from '../api/order'
import type { Ebook, EbookPageAccess } from '../types'

const { Title, Paragraph } = Typography

function EbookDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [ebook, setEbook] = useState<Ebook | null>(null)
  const [access, setAccess] = useState<EbookPageAccess | null>(null)
  const [loading, setLoading] = useState(false)
  const [purchasing, setPurchasing] = useState(false)

  useEffect(() => {
    if (id) {
      loadEbookDetail()
    }
  }, [id])

  const loadEbookDetail = async () => {
    if (!id) return
    setLoading(true)
    try {
      const res = await ebookApi.getById(id)
      setEbook(res.data?.data || res.data)
      // 授权状态以服务端为准（已购/试读边界/下架）
      try {
        const accessRes = await ebookApi.getAccess(id)
        setAccess(accessRes.data?.data || null)
      } catch {
        setAccess(null)
      }
    } catch (error) {
      console.error('Failed to load ebook:', error)
    } finally {
      setLoading(false)
    }
  }

  const handlePurchase = async () => {
    if (!id) return
    setPurchasing(true)
    try {
      // 1. 创建/回读订单（重复或并发点击只会得到同一个有效订单）
      const orderRes = await ebookApi.purchase(id)
      const order = orderRes.data?.data
      if (!order) {
        message.error(orderRes.data?.message || '下单失败')
        return
      }
      if (order.status === 'PAID') {
        message.success('您已购买本书，可直接阅读全文')
        setAccess((prev) => (prev ? { ...prev, purchased: true } : prev))
        navigate(`/ebooks/read/${id}`)
        return
      }
      // 2. 模拟支付宝沙箱支付确认；支付成功后立即解锁
      const payRes = await orderApi.pay(order.id)
      const paid = payRes.data?.data
      if (paid?.status === 'PAID') {
        message.success('支付成功，已解锁全文')
        setAccess((prev) => (prev ? { ...prev, purchased: true } : prev))
        navigate(`/ebooks/read/${id}`)
      } else {
        message.error(payRes.data?.message || '支付失败')
      }
    } catch (error) {
      // 支付失败时订单仍为待支付，不会出现已购状态
      console.error('Purchase failed:', error)
    } finally {
      setPurchasing(false)
    }
  }

  if (loading || !ebook) {
    return <Spin style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />
  }

  const offline = ebook.status === 'OFFLINE'

  return (
    <div>
      <Card>
        {offline && (
          <Alert
            style={{ marginBottom: 16 }}
            type="warning"
            showIcon
            message="该书已被作者下架"
            description={access?.purchased ? '您已购买，仍可继续阅读全文。' : '已停止售卖，未购买用户无法购买和试读。'}
          />
        )}
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
            <Title level={2}>{ebook.title}</Title>
            <div style={{ marginBottom: 16 }}>
              <Avatar icon={<span>👤</span>} src={ebook.creator?.avatar} />
              <span style={{ marginLeft: 8 }}>{ebook.creator?.username}</span>
              <Tag color="green" style={{ marginLeft: 8 }}>
                {ebook.fileType}
              </Tag>
              {offline && <Tag color="red">已下架</Tag>}
            </div>
            <Paragraph type="secondary">{ebook.description}</Paragraph>
            <Descriptions column={2} style={{ marginTop: 16 }}>
              {ebook.pageCount != null && (
                <Descriptions.Item label="实际页数">{ebook.pageCount}</Descriptions.Item>
              )}
              {ebook.wordCount != null && (
                <Descriptions.Item label="字数">{ebook.wordCount}字</Descriptions.Item>
              )}
              <Descriptions.Item label="试读">
                前{Math.round((ebook.sampleEndPercent || 0.1) * 100)}%免费（第1-
                {access?.sampleEndPage ?? '-'}页）
              </Descriptions.Item>
              <Descriptions.Item label="价格" className="price-text">
                ¥{ebook.price}
              </Descriptions.Item>
            </Descriptions>
            <div style={{ marginTop: 24, gap: 12, display: 'flex' }}>
              {access?.purchased ? (
                <Button
                  type="primary"
                  size="large"
                  onClick={() => navigate(`/ebooks/read/${ebook.id}`)}
                >
                  开始阅读（已购）
                </Button>
              ) : (
                !offline && (
                  <Button
                    type="primary"
                    size="large"
                    onClick={handlePurchase}
                    loading={purchasing}
                  >
                    立即购买
                  </Button>
                )
              )}
              {!offline && (
                <Button size="large" onClick={() => navigate(`/ebooks/read/${ebook.id}`)}>
                  免费试读
                </Button>
              )}
            </div>
          </div>
        </div>
      </Card>
    </div>
  )
}

export default EbookDetail
