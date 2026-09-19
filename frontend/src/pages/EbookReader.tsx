import { useCallback, useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Card,
  Button,
  Space,
  Switch,
  Select,
  Typography,
  message,
  Spin,
  Modal,
} from 'antd'
import {
  LeftOutlined,
  RightOutlined,
  SettingOutlined,
  MoonOutlined,
  SunOutlined,
  PushpinOutlined,
  ArrowLeftOutlined,
} from '@ant-design/icons'
import { ebookApi } from '../api/ebook'
import { orderApi } from '../api/order'
import type { PageContent, ReaderSession } from '../types'

const { Title, Paragraph } = Typography

function EbookReader() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [session, setSession] = useState<ReaderSession | null>(null)
  const [pageContent, setPageContent] = useState<PageContent | null>(null)
  const [loading, setLoading] = useState(false)
  const [paying, setPaying] = useState(false)
  const [denied, setDenied] = useState<string | null>(null)
  const [attemptedPage, setAttemptedPage] = useState<number | null>(null)
  const [fontSize, setFontSize] = useState(16)
  const [nightMode, setNightMode] = useState(false)
  const [bookmarks, setBookmarks] = useState<number[]>([])
  const [settingsVisible, setSettingsVisible] = useState(false)

  /**
   * 读取某一页。试读边界、是否已购完全由服务端判定：
   * 无论翻页、重新打开还是直接访问阅读入口，越界请求都只会得到拒绝而不是正文。
   */
  const loadPage = useCallback(
    async (page: number, showError = true) => {
      if (!id) return
      setLoading(true)
      setDenied(null)
      try {
        const res = await ebookApi.getPage(id, page)
        const body = res.data
        if (body && body.success === false) {
          setDenied(body.message || '试读范围到此结束，购买后可阅读全文')
          setPageContent(null)
          return
        }
        const data: PageContent = body?.data ?? body
        setPageContent(data)
        setAttemptedPage(null)
        setSession((prev) =>
          prev ? { ...prev, currentPage: data.page, purchased: data.purchased } : prev
        )
      } catch (error: any) {
        const msg =
          error?.response?.data?.message || '试读范围到此结束，购买后可阅读全文'
        if (showError) message.error(msg)
        setDenied(msg)
        setAttemptedPage(page)
        setPageContent(null)
      } finally {
        setLoading(false)
      }
    },
    [id]
  )

  useEffect(() => {
    if (!id) return
    let cancelled = false
    setLoading(true)
    ebookApi
      .openReader(id)
      .then((res) => {
        if (cancelled) return
        const body = res.data
        const data: ReaderSession = body?.data ?? body
        setSession(data)
        // 续读页码由服务端按授权收敛，未购买者不可能从越界页开始
        loadPage(data.currentPage || 1, false)
      })
      .catch((error) => {
        if (cancelled) return
        const msg = error?.response?.data?.message || '无法打开该电子书'
        setDenied(msg)
        message.error(msg)
        setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [id, loadPage])

  const toggleBookmark = () => {
    if (!session) return
    const page = pageContent?.page ?? session.currentPage
    if (bookmarks.includes(page)) {
      setBookmarks(bookmarks.filter((p) => p !== page))
      message.info('已取消书签')
    } else {
      setBookmarks([...bookmarks, page])
      message.success('已添加书签')
    }
  }

  /** 下单 + 支付为同一闭环：幂等下单，支付成功后立即解锁并回读当前页 */
  const handlePurchaseAndPay = async () => {
    if (!id) return
    setPaying(true)
    try {
      const orderRes = await orderApi.createEbookOrder(id)
      const orderBody = orderRes.data
      const order = orderBody?.data ?? orderBody
      const payRes = await orderApi.pay(order.id, 'SUCCESS')
      const payBody = payRes.data
      if (payBody && payBody.success === false) {
        message.error(payBody.message || '支付失败')
        return
      }
      const paid = payBody?.data ?? payBody
      if (paid.status !== 'PAID') {
        message.error('支付未完成，请稍后重试')
        return
      }
      message.success('支付成功，已解锁全文')
      setSession((prev) =>
        prev ? { ...prev, purchased: true, currentPage: attemptedPage ?? pageContent?.page ?? 1 } : prev
      )
      // 支付成功后立即解锁：回到刚才被拦的那一页
      loadPage(attemptedPage ?? pageContent?.page ?? 1)
    } catch (error: any) {
      message.error(error?.response?.data?.message || '支付失败，请稍后重试')
    } finally {
      setPaying(false)
    }
  }

  const goPage = (page: number) => {
    if (!session) return
    if (page < 1 || page > session.pageCount) return
    loadPage(page)
  }

  const currentPage = pageContent?.page ?? session?.currentPage ?? 1
  const maxPage = session?.pageCount ?? 1
  // 未购买时服务端只允许试读页；next 按钮同样不能越过边界
  const lockedBoundary = session ? (session.purchased ? maxPage : session.sampleEndPage) : 1

  const fontSizeOptions = [
    { value: 12, label: '小' },
    { value: 14, label: '较小' },
    { value: 16, label: '中' },
    { value: 18, label: '较大' },
    { value: 20, label: '大' },
  ]

  if (loading && !session) {
    return <Spin style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />
  }

  return (
    <div style={{ padding: 0 }}>
      <Card
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 100,
          background: nightMode ? '#141414' : '#fff',
          color: nightMode ? '#fff' : 'inherit',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(`/ebooks/${id}`)}>
              返回
            </Button>
            <Title level={4} style={{ margin: 0 }}>
              {session?.title}
            </Title>
          </Space>
          <Space>
            <Button
              icon={<PushpinOutlined />}
              onClick={toggleBookmark}
              type={bookmarks.includes(currentPage) ? 'primary' : 'default'}
            >
              书签
            </Button>
            <Button
              icon={nightMode ? <SunOutlined /> : <MoonOutlined />}
              onClick={() => setNightMode(!nightMode)}
            >
              {nightMode ? '日间' : '夜间'}
            </Button>
            <Button icon={<SettingOutlined />} onClick={() => setSettingsVisible(true)}>
              设置
            </Button>
          </Space>
        </div>
      </Card>

      <div
        style={{
          padding: 48,
          minHeight: 'calc(100vh - 200px)',
          background: nightMode ? '#1f1f1f' : '#fafafa',
          color: nightMode ? '#d9d9d9' : '#333',
          fontSize: `${fontSize}px`,
          lineHeight: 1.8,
        }}
      >
        {pageContent ? (
          <div style={{ maxWidth: 800, margin: '0 auto' }}>
            <div style={{ textAlign: 'center', marginBottom: 32 }}>
              <h2>第 {pageContent.page} 页</h2>
            </div>
            <p style={{ textIndent: '2em', whiteSpace: 'pre-wrap' }}>
              {pageContent.content}
            </p>
            {pageContent.sample && (
              <Paragraph type="secondary" style={{ marginTop: 32, textAlign: 'center' }}>
                试读范围：前 {session?.sampleEndPage} 页（全书共 {pageContent.pageCount} 页的 10%）
              </Paragraph>
            )}
          </div>
        ) : (
          <div style={{ textAlign: 'center', padding: 100 }}>
            <Title level={3}>
              {session?.purchased ? '内容不可用' : '试读结束'}
            </Title>
            <p style={{ margin: '24px 0' }}>
              {denied || '您已阅读到试读部分的末尾，购买后可继续阅读完整内容。'}
            </p>
            {!session?.purchased && (
              <Button
                type="primary"
                size="large"
                loading={paying}
                onClick={handlePurchaseAndPay}
              >
                购买并解锁整本电子书
              </Button>
            )}
          </div>
        )}
      </div>

      <Card style={{ position: 'sticky', bottom: 0 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Button
            disabled={currentPage <= 1}
            icon={<LeftOutlined />}
            onClick={() => goPage(currentPage - 1)}
          >
            上一页
          </Button>
          <span>
            第 {currentPage} / {maxPage} 页
            {session && !session.purchased && `（试读到第 ${session.sampleEndPage} 页）`}
          </span>
          <Button
            disabled={currentPage >= lockedBoundary}
            icon={<RightOutlined />}
            onClick={() => goPage(currentPage + 1)}
          >
            下一页
          </Button>
        </div>
      </Card>

      <Modal
        title="阅读设置"
        open={settingsVisible}
        onCancel={() => setSettingsVisible(false)}
        footer={null}
      >
        <div style={{ marginBottom: 24 }}>
          <p style={{ marginBottom: 8 }}>字体大小：</p>
          <Select
            value={fontSize}
            onChange={setFontSize}
            style={{ width: 200 }}
            options={fontSizeOptions}
          />
        </div>
        <div>
          <p style={{ marginBottom: 8 }}>夜间模式：</p>
          <Switch checked={nightMode} onChange={setNightMode} />
        </div>
      </Modal>
    </div>
  )
}

export default EbookReader
