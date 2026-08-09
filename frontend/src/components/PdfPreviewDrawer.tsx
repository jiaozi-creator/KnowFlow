import { useEffect, useRef, useState } from 'react'
import { Button, Drawer, Empty, Space, Spin, Typography } from 'antd'
import * as pdfjsLib from 'pdfjs-dist'
import { useAuthStore } from '../store/auth'
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorkerUrl

interface Props {
  open: boolean
  url?: string
  title?: string
  initialPage?: number
  onClose: () => void
}

export function PdfPreviewDrawer({ open, url, title, initialPage = 1, onClose }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [page, setPage] = useState(initialPage)
  const [pages, setPages] = useState(0)
  const [loading, setLoading] = useState(false)
  const accessToken = useAuthStore((state) => state.accessToken)

  useEffect(() => setPage(initialPage), [initialPage, url])

  useEffect(() => {
    if (!open || !url || !canvasRef.current) return
    let cancelled = false
    const render = async () => {
      setLoading(true)
      const pdf = await pdfjsLib.getDocument({
        url,
        httpHeaders: accessToken ? { Authorization: `Bearer ${accessToken}` } : undefined,
      }).promise
      if (cancelled) return
      setPages(pdf.numPages)
      const safePage = Math.max(1, Math.min(page, pdf.numPages))
      const pdfPage = await pdf.getPage(safePage)
      const viewport = pdfPage.getViewport({ scale: 1.35 })
      const canvas = canvasRef.current!
      const context = canvas.getContext('2d')!
      canvas.width = viewport.width
      canvas.height = viewport.height
      await pdfPage.render({ canvas, canvasContext: context, viewport }).promise
      setLoading(false)
    }
    render().catch(() => setLoading(false))
    return () => { cancelled = true }
  }, [open, url, page, accessToken])

  return (
    <Drawer open={open} onClose={onClose} title={title ?? '文档预览'} width={760}>
      {!url ? <Empty description="暂无预览地址" /> : (
        <div className="pdf-preview">
          <Space className="pdf-toolbar">
            <Button disabled={page <= 1} onClick={() => setPage((value) => value - 1)}>上一页</Button>
            <Typography.Text>{page} / {pages || '-'}</Typography.Text>
            <Button disabled={!pages || page >= pages} onClick={() => setPage((value) => value + 1)}>下一页</Button>
          </Space>
          <Spin spinning={loading}>
            <canvas ref={canvasRef} className="pdf-canvas" />
          </Spin>
        </div>
      )}
    </Drawer>
  )
}
