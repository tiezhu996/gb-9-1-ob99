import { useEffect, useState, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Card,
  Button,
  Space,
  Switch,
  Select,
  Typography,
  Spin,
  Modal,
  Result,
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
import type { EbookPageAccess } from '../types'

const { Title, Paragraph } = Typography

function EbookReader() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [access, setAccess] = useState<EbookPageAccess | null>(null)
  const [content, setContent] = useState<string>('')
  const [locked, setLocked] = useState(false)
  const [denied, setDenied] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [currentPage, setCurrentPage] = useState(1)
  const [fontSize, setFontSize] = useState(16)
  const [nightMode, setNightMode] = useState(false)
  const [bookmarks, setBookmarks] = useState<number[]>([])
  const [settingsVisible, setSettingsVisible] = useState(false)

  const totalPages = access?.totalPages ?? 0
  const sampleEndPage = access?.sampleEndPage ?? 0
  const purchased = access?.purchased ?? false

  // 每一页都向服务端请求并由服务端授权。
  // 翻页、重新打开阅读器、直接在地址栏输入超界页码，都会走同一校验，无法越过试读边界。
  const loadPage = useCallback(
    async (page: number) => {
      if (!id) return
      setLoading(true)
      try {
        const res = await ebookApi.getPage(id, page)
        const body = res.data?.data
        if (res.data?.success && body && !body.locked) {
          setAccess((prev) => ({ ...(prev as EbookPageAccess), ...body }))
          setContent(body.content || '')
          setLocked(false)
          setCurrentPage(page)
          localStorage.setItem(`ebook-progress:${id}`, String(page))
        } else {
          // 服务端拒绝（越过试读边界），不渲染任何正文
          if (body) {
            setAccess((prev) => ({ ...(prev as EbookPageAccess), ...body }))
          }
          setContent('')
          setLocked(true)
          setCurrentPage(page)
        }
      } catch (error: any) {
        const body = error.response?.data?.data
        const msg = error.response?.data?.message
        if (body && typeof body.purchased === 'boolean') {
          setAccess((prev) => ({ ...(prev as EbookPageAccess), ...body }))
          setContent('')
          setLocked(true)
          setCurrentPage(body.page || page)
        } else {
          // 下架/不存在：整个阅读入口不可用
          setDenied(msg || '无法阅读该电子书')
        }
      } finally {
        setLoading(false)
      }
    },
    [id]
  )

  useEffect(() => {
    if (!id) return
    ;(async () => {
      setLoading(true)
      try {
        const res = await ebookApi.getAccess(id)
        const acc: EbookPageAccess = res.data?.data
        if (!res.data?.success || !acc) {
          setDenied(res.data?.message || '无法阅读该电子书')
          setLoading(false)
          return
        }
        setAccess(acc)
        setBookmarks(JSON.parse(localStorage.getItem(`ebook-bookmarks:${id}`) || '[]'))

        // 恢复上次阅读进度（重新打开）。恢复的页码同样要经服务端授权，
        // 未购买读者即使本地存了更大的页码，也只能被挡在试读边界。
        const saved = parseInt(localStorage.getItem(`ebook-progress:${id}`) || '1', 10)
        const startPage = acc.purchased
          ? Math.min(Math.max(saved, 1), acc.totalPages || 1)
          : Math.min(Math.max(saved, 1), Math.max(acc.sampleEndPage, 1))
        await loadPage(startPage)
      } catch (error: any) {
        setDenied(error.response?.data?.message || '无法阅读该电子书')
      } finally {
        setLoading(false)
      }
    })()
  }, [id, loadPage])

  const goPage = (page: number) => {
    if (page < 1 || page > totalPages) return
    loadPage(page)
  }

  const toggleBookmark = () => {
    const next = bookmarks.includes(currentPage)
      ? bookmarks.filter((p) => p !== currentPage)
      : [...bookmarks, currentPage]
    setBookmarks(next)
    localStorage.setItem(`ebook-bookmarks:${id}`, JSON.stringify(next))
  }

  const fontSizeOptions = [
    { value: 12, label: '小' },
    { value: 14, label: '较小' },
    { value: 16, label: '中' },
    { value: 18, label: '较大' },
    { value: 20, label: '大' },
  ]

  if (denied) {
    return (
      <Result
        status="warning"
        title="无法阅读"
        subTitle={denied}
        extra={
          <Button type="primary" onClick={() => navigate(`/ebooks/${id}`)}>
            返回电子书详情
          </Button>
        }
      />
    )
  }

  if (loading || !access) {
    return <Spin style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />
  }

  // 未购买读者“下一页”按钮在试读最后一页即禁用；
  // 即便绕过按钮（直接改 URL/状态），服务端也不会返回超界正文。
  const canGoNext = currentPage < totalPages && (purchased || currentPage < sampleEndPage)
  const canGoPrev = currentPage > 1

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
              {access.title}
            </Title>
          </Space>
          <Space>
            <Button
              icon={<PushpinOutlined />}
              onClick={toggleBookmark}
              type={bookmarks.includes(currentPage) ? 'primary' : 'default'}
              disabled={locked}
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
        {locked ? (
          <div style={{ textAlign: 'center', padding: 100 }}>
            <Title level={3}>试读结束</Title>
            <Paragraph style={{ margin: '24px 0' }}>
              免费试读范围为全书前
              {Math.round((sampleEndPage / Math.max(totalPages, 1)) * 100)}%
              （第 1-{sampleEndPage} 页，共 {totalPages} 页）。您当前请求的是第 {currentPage} 页，
              购买后可继续阅读完整内容。
            </Paragraph>
            <Button type="primary" size="large" onClick={() => navigate(`/ebooks/${id}`)}>
              购买整本电子书
            </Button>
          </div>
        ) : (
          <div style={{ maxWidth: 800, margin: '0 auto' }}>
            <div style={{ textAlign: 'center', marginBottom: 32 }}>
              <h2>
                第 {currentPage} 页{purchased ? '' : ' · 试读'}
              </h2>
            </div>
            {content.split('\n').map((para, i) =>
              para.trim() ? (
                <p key={i} style={{ textIndent: '2em' }}>
                  {para}
                </p>
              ) : null
            )}
          </div>
        )}
      </div>

      <Card style={{ position: 'sticky', bottom: 0 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Button disabled={!canGoPrev} icon={<LeftOutlined />} onClick={() => goPage(currentPage - 1)}>
            上一页
          </Button>
          <span>
            第 {currentPage} / {totalPages} 页
            {!purchased && `（试读到第 ${sampleEndPage} 页）`}
          </span>
          <Button disabled={!canGoNext} icon={<RightOutlined />} onClick={() => goPage(currentPage + 1)}>
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
